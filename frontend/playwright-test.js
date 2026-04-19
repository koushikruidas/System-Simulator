import { chromium } from 'playwright';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SCREENSHOTS = path.join(__dirname, 'test-screenshots');
const BASE = 'http://localhost:5173';

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ✅ ${message}`);
    passed++;
  } else {
    console.error(`  ❌ ${message}`);
    failed++;
  }
}

async function screenshot(page, name) {
  import('fs').then(fs => fs.default.mkdirSync(SCREENSHOTS, { recursive: true }));
  await page.screenshot({ path: path.join(SCREENSHOTS, `${name}.png`), fullPage: false });
}

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 200 });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1400, height: 900 });

  try {
    // ── 1. Initial load ───────────────────────────────────────────────────────
    console.log('\n📋 Test 1: Initial load');
    await page.goto(BASE, { waitUntil: 'networkidle' });
    await screenshot(page, '01-initial');

    assert(await page.title() === 'System Simulator', 'Page title is "System Simulator"');
    assert(await page.isVisible('text=🔬 System Simulator'), 'Header renders');
    assert(await page.isVisible('text=▶ Run Simulation'), 'Run button visible');
    assert(await page.isVisible('text=Nodes'), 'Nodes section visible');
    // Connections may be below the fold in the scrollable config panel
    assert(await page.locator('text=Connections').count() > 0, 'Connections section in DOM');

    // ── 2. Graph renders default topology ────────────────────────────────────
    console.log('\n📋 Test 2: Default topology graph');
    // React Flow renders asynchronously — wait for nodes to appear
    const rfContainer = await page.$('.react-flow');
    assert(rfContainer !== null, 'React Flow container present');
    await page.waitForSelector('.react-flow__node', { timeout: 5000 });
    const nodeCount = await page.locator('.react-flow__node').count();
    assert(nodeCount === 3, `3 React Flow nodes rendered (got ${nodeCount})`);
    assert(await page.locator('.react-flow__node').filter({ hasText: 'lb' }).count() > 0, 'lb node in graph');
    assert(await page.locator('.react-flow__node').filter({ hasText: 'service' }).count() > 0, 'service node in graph');
    assert(await page.locator('.react-flow__node').filter({ hasText: 'db' }).count() > 0, 'db node in graph');

    // ── 3. Preset buttons ────────────────────────────────────────────────────
    console.log('\n📋 Test 3: "2-Service LB" preset');
    await page.click('text=2-Service LB');
    // Wait for React Flow to remount and render the new topology
    await page.waitForSelector('.react-flow__node', { timeout: 5000 });
    await page.waitForFunction(() => document.querySelectorAll('.react-flow__node').length === 4, { timeout: 5000 });
    await page.waitForTimeout(300);
    await screenshot(page, '02-2service-preset');

    assert(await page.locator('.react-flow__node').filter({ hasText: 's1' }).count() > 0, 's1 node in graph after preset');
    assert(await page.locator('.react-flow__node').filter({ hasText: 's2' }).count() > 0, 's2 node in graph after preset');

    // ── 4. Run simple simulation ──────────────────────────────────────────────
    console.log('\n📋 Test 4: Run simple simulation');
    await page.click('text=Simple');
    await page.waitForTimeout(300);
    await page.click('text=▶ Run Simulation');
    await page.waitForSelector('text=request-1', { timeout: 8000 });
    await screenshot(page, '03-simple-result');

    assert(await page.isVisible('text=request-1'), 'request-1 appears in result list');
    assert(await page.isVisible('text=✓ OK'), 'COMPLETED status badge shown');
    assert(await page.isVisible('text=avg'), 'Average latency shown in header');

    // Check breakdown section appears (request auto-selected)
    assert(await page.isVisible('text=Latency Breakdown') || await page.isVisible('text=▶ Animate'), 'Breakdown panel visible');

    // ── 5. Latency breakdown bars ─────────────────────────────────────────────
    console.log('\n📋 Test 5: Latency breakdown for request-1');
    await page.click('text=request-1');
    await page.waitForTimeout(300);
    await screenshot(page, '04-breakdown');

    assert(await page.isVisible('text=lb'), 'lb row in breakdown');
    assert(await page.isVisible('text=service'), 'service row in breakdown');
    assert(await page.isVisible('text=db'), 'db row in breakdown');
    assert(await page.isVisible('text=16ms'), 'total latency 16ms shown');
    assert(await page.isVisible('text=▶ Animate'), 'Animate button present');

    // ── 6. Animation ─────────────────────────────────────────────────────────
    console.log('\n📋 Test 6: Animate request path');
    await page.click('text=▶ Animate');
    await page.waitForTimeout(400);
    await screenshot(page, '05-animating');
    assert(await page.isVisible('text=⏸ Pause'), 'Pause button appears during animation');

    await page.waitForTimeout(3000); // let animation complete (3 hops × 900ms)
    await screenshot(page, '06-animation-done');
    assert(await page.isVisible('text=▶ Animate'), 'Animate button returns after completion');

    // ── 7. Run 2-service round-robin simulation ───────────────────────────────
    console.log('\n📋 Test 7: 2-service RR simulation (6 requests)');
    await page.click('text=2-Service LB');
    await page.waitForTimeout(300);
    await page.click('text=▶ Run Simulation');
    await page.waitForSelector('text=request-6', { timeout: 8000 });
    await screenshot(page, '07-2service-result');

    assert(await page.isVisible('text=request-1'), 'request-1 in list');
    assert(await page.isVisible('text=request-6'), 'request-6 in list (6 requests total)');

    // Header should show s1 and s2 metrics
    const headerText = await page.textContent('header');
    assert(headerText.includes('s1'), 's1 metrics in header');
    assert(headerText.includes('s2'), 's2 metrics in header');

    // Select a request and verify breakdown path
    await page.click('text=request-1');
    await page.waitForTimeout(300);
    await screenshot(page, '08-2service-breakdown');

    // Should contain s1 or s2 in breakdown (depending on RR assignment)
    const breakdownText = await page.textContent('.flex-1');
    const hasS1OrS2 = breakdownText.includes('s1') || breakdownText.includes('s2');
    assert(hasS1OrS2, 'Breakdown shows s1 or s2 (RR routing)');

    // ── 8. LB distribution shown on node ─────────────────────────────────────
    console.log('\n📋 Test 8: LB distribution on graph node');
    // The lb node in the graph should show distribution
    const graphArea = await page.$('.react-flow');
    const graphText = await graphArea.textContent();
    const hasDistribution = graphText.includes('s1') && graphText.includes('req');
    assert(hasDistribution, 'LB node shows downstream distribution');

    // ── 9. Error handling — invalid scenario ─────────────────────────────────
    console.log('\n📋 Test 9: Error handling');
    // Remove all connections to trigger validation error
    const removeButtons = await page.$$('button:has-text("✕")');
    // Remove connection buttons (last two)
    for (const btn of removeButtons.slice(-4)) {
      await btn.click().catch(() => {});
      await page.waitForTimeout(100);
    }
    await page.click('text=▶ Run Simulation');
    await page.waitForTimeout(2000);
    await screenshot(page, '09-error');
    const errorEl = await page.$('.text-red-600');
    assert(errorEl !== null, 'Error message displays on validation failure');

    // ── Summary ───────────────────────────────────────────────────────────────
    console.log(`\n─────────────────────────────────────`);
    console.log(`Results: ${passed} passed, ${failed} failed`);
    console.log(`Screenshots saved to: ${SCREENSHOTS}`);

  } catch (err) {
    console.error('\n💥 Unexpected error:', err.message);
    await screenshot(page, 'error-crash');
    failed++;
  } finally {
    await browser.close();
  }

  process.exit(failed > 0 ? 1 : 0);
})();
