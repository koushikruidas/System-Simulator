import { chromium } from 'playwright';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHOTS = path.join(__dirname, 'test-screenshots');
const BASE = 'http://localhost:5173';

let passed = 0, failed = 0;

function assert(ok, msg) {
  if (ok) { console.log(`  ✅ ${msg}`); passed++; }
  else     { console.error(`  ❌ ${msg}`); failed++; }
}
async function shot(page, name) {
  const { default: fs } = await import('fs');
  fs.mkdirSync(SHOTS, { recursive: true });
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`) });
}
async function waitNodes(page, count, timeout = 6000) {
  await page.waitForFunction(
    n => document.querySelectorAll('.react-flow__node').length === n,
    count, { timeout }
  );
}

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 150 });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1400, height: 900 });

  try {

    // ── 1. Initial load ────────────────────────────────────────────────────
    console.log('\n📋 Test 1: Initial load');
    await page.goto(BASE, { waitUntil: 'networkidle' });
    await shot(page, '01-initial');
    assert(await page.title() === 'System Simulator',   'Page title correct');
    assert(await page.isVisible('text=🔬 System Simulator'), 'Header visible');
    assert(await page.isVisible('text=▶ Run Simulation'),    'Run button visible');
    assert(await page.isVisible('text=Presets'),             'Presets section visible');
    assert(await page.isVisible('text=+ Add Layer'),         'Add Layer button visible');

    // ── 2. Default topology graph (3 nodes: lb, svc, db) ─────────────────
    console.log('\n📋 Test 2: Default topology graph');
    await waitNodes(page, 3);
    const nodeCount = await page.locator('.react-flow__node').count();
    assert(nodeCount === 3, `3 nodes in graph (got ${nodeCount})`);
    assert(await page.locator('.react-flow__node').filter({ hasText: 'lb' }).count() > 0,  'lb node rendered');
    assert(await page.locator('.react-flow__node').filter({ hasText: 'svc' }).count() > 0, 'svc node rendered');
    assert(await page.locator('.react-flow__node').filter({ hasText: 'db' }).count() > 0,  'db node rendered');

    // ── 3. Node count + connection count shown ────────────────────────────
    console.log('\n📋 Test 3: Topology stats');
    assert(await page.locator('text=3 nodes').count() > 0,       '3 nodes stat shown');
    assert(await page.locator('text=2 connections').count() > 0,  '2 connections stat shown');

    // ── 4. "2-Service LB" preset ──────────────────────────────────────────
    console.log('\n📋 Test 4: Preset — 2-Service LB');
    await page.click('text=2-Service LB');
    await waitNodes(page, 4);
    await shot(page, '02-2service-preset');
    assert(await page.locator('.react-flow__node').count() === 4, '4 nodes after preset');
    assert(await page.locator('text=4 nodes').count() > 0,        '4 nodes stat shown');
    assert(await page.locator('text=4 connections').count() > 0,  '4 connections stat shown');

    // ── 5. Scaled preset ──────────────────────────────────────────────────
    console.log('\n📋 Test 5: Preset — Scaled (2-10-2)');
    await page.click('text=Scaled (2-10-2)');
    await waitNodes(page, 14);
    await shot(page, '03-scaled-preset');
    assert(await page.locator('.react-flow__node').count() === 14, '14 nodes for scaled preset');

    // ── 6. Add a layer ────────────────────────────────────────────────────
    console.log('\n📋 Test 6: Add Layer');
    await page.click('text=Simple (1-1-1)'); // reset to simple first
    await waitNodes(page, 3);
    await page.click('text=+ Add Layer');
    await waitNodes(page, 4);
    assert(await page.locator('.react-flow__node').count() === 4, '4 nodes after adding layer');

    // ── 7. Duplicate a layer ──────────────────────────────────────────────
    console.log('\n📋 Test 7: Duplicate layer');
    await page.click('text=Simple (1-1-1)');
    await waitNodes(page, 3);
    const dupBtns = page.locator('button[title="Duplicate"]');
    await dupBtns.first().click();
    await page.waitForTimeout(300);
    await shot(page, '04-duplicate');
    assert(await page.locator('[title="Duplicate"]').count() > 0, 'Duplicate button worked');

    // ── 8. Run simple simulation ──────────────────────────────────────────
    console.log('\n📋 Test 8: Run simple simulation');
    await page.click('text=Simple (1-1-1)');
    await waitNodes(page, 3);
    await page.click('text=▶ Run Simulation');
    await page.waitForSelector('text=request-1', { timeout: 8000 });
    await shot(page, '05-simple-result');
    assert(await page.isVisible('text=request-1'),    'request-1 in list');
    assert(await page.isVisible('text=✓ OK'),         'COMPLETED badge shown');
    assert(await page.isVisible('text=avg'),          'avg latency in header');
    assert(await page.isVisible('text=▶ Animate'),    'Animate button present');

    // ── 9. Latency breakdown (16ms = 1+5+10) ─────────────────────────────
    console.log('\n📋 Test 9: Latency breakdown');
    await page.click('text=request-1');
    await page.waitForTimeout(300);
    await shot(page, '06-breakdown');
    assert(await page.isVisible('text=16ms'), 'Total latency 16ms shown');
    assert(await page.locator('text=lb').count() > 0,  'lb in breakdown');
    assert(await page.locator('text=svc').count() > 0, 'svc in breakdown');
    assert(await page.locator('text=db').count() > 0,  'db in breakdown');

    // ── 10. Animate ───────────────────────────────────────────────────────
    console.log('\n📋 Test 10: Animation');
    await page.click('text=▶ Animate');
    await page.waitForTimeout(400);
    assert(await page.isVisible('text=⏸ Pause'), 'Pause button appears');
    await page.waitForTimeout(3000);
    assert(await page.isVisible('text=▶ Animate'), 'Animate button returns after completion');

    // ── 11. 2-Service RR simulation ───────────────────────────────────────
    console.log('\n📋 Test 11: 2-service Round Robin simulation');
    await page.click('text=2-Service LB');
    await waitNodes(page, 4);
    await page.click('text=▶ Run Simulation');
    await page.waitForSelector('text=request-6', { timeout: 8000 });
    await shot(page, '07-2service-result');
    assert(await page.isVisible('text=request-6'),  'request-6 present (6 requests)');
    const header = await page.textContent('header');
    assert(header.includes('svc-1'), 'svc-1 in header metrics');
    assert(header.includes('svc-2'), 'svc-2 in header metrics');

    // ── 12. LB distribution on graph node ────────────────────────────────
    console.log('\n📋 Test 12: LB distribution on graph node');
    await page.click('text=request-1');
    await page.waitForTimeout(300);
    await shot(page, '08-lb-distribution');
    const graphText = await page.locator('.react-flow').textContent();
    assert(graphText.includes('svc'), 'svc mentioned in LB distribution');

    // ── 13. Scaled simulation (2-10-2, 20 requests) ───────────────────────
    console.log('\n📋 Test 13: Scaled simulation (14 nodes, 20 requests)');
    await page.click('text=Scaled (2-10-2)');
    await waitNodes(page, 14);
    await page.click('text=▶ Run Simulation');
    await page.waitForSelector('text=request-20', { timeout: 12000 });
    await shot(page, '09-scaled-result');
    assert(await page.isVisible('text=request-20'), 'All 20 requests returned');
    assert(await page.locator('text=20 nodes').count() === 0, 'Node stat not polluted');

    // ── 14. Error handling ────────────────────────────────────────────────
    console.log('\n📋 Test 14: Error handling — remove all layers');
    await page.click('text=Simple (1-1-1)');
    await waitNodes(page, 3);
    const removeBtns = page.locator('button[title="Remove"]');
    const cnt = await removeBtns.count();
    for (let i = cnt - 1; i >= 0; i--) {
      await removeBtns.nth(i).click();
      await page.waitForTimeout(150);
    }
    assert(await page.locator('button[disabled]').filter({ hasText: 'Run Simulation' }).count() > 0,
      'Run button disabled when no layers');

    // ── Summary ────────────────────────────────────────────────────────────
    console.log(`\n──────────────────────────────────────`);
    console.log(`Results: ${passed} passed, ${failed} failed`);
    console.log(`Screenshots: ${SHOTS}`);

  } catch (e) {
    console.error('\n💥 Crash:', e.message);
    await shot(page, 'crash');
    failed++;
  } finally {
    await browser.close();
  }

  process.exit(failed > 0 ? 1 : 0);
})();
