import { chromium } from 'playwright';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHOTS = path.join(__dirname, 'test-screenshots');
const BASE = 'http://localhost:5173';
const API  = 'http://localhost:8080';

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

async function simulate(body) {
  const res = await fetch(`${API}/simulate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return res.json();
}

const LB_NODE    = { id: 'lb',    type: 'LOAD_BALANCER', capacity: 10, queueLimit: 10, latency: 0,  strategy: 'ROUND_ROBIN' };
const CACHE_NODE = { id: 'cache', type: 'CACHE',         capacity: 10, queueLimit: 10, latency: 10, hitRate: 0.5, hitLatency: 2 };
const DB_NODE    = { id: 'db',    type: 'DATABASE',       capacity: 10, queueLimit: 10, latency: 10 };
const LINKS      = [{ sourceNodeId: 'lb', targetNodeId: 'cache' }, { sourceNodeId: 'cache', targetNodeId: 'db' }];

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 120 });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1400, height: 900 });

  try {

    // ── Test 15: CACHE type appears in the layer type selector ───────────────
    console.log('\n📋 Test 15: CACHE type available in layer type selector');
    await page.goto(BASE, { waitUntil: 'networkidle' });
    await page.click('text=Simple (1-1-1)');
    await waitNodes(page, 3);
    await page.click('text=+ Add Layer');
    await waitNodes(page, 4);
    await page.waitForTimeout(300);
    // The last select is the type-select for the newly added layer
    await page.locator('select').last().selectOption('CACHE');
    await page.waitForTimeout(400);
    await shot(page, 'cache-15-layer-added');
    assert(await page.locator('text=Hit Rate').count() > 0,    'Hit Rate field shown for CACHE layer');
    assert(await page.locator('text=Hit Latency').count() > 0, 'Hit Latency field shown for CACHE layer');

    // ── Test 16: hitRate=1.0 — all requests served from cache ───────────────
    console.log('\n📋 Test 16: hitRate=1.0 — all requests terminate at cache');
    const allHits = await simulate({
      requestCount: 5, entryNodeId: 'lb',
      nodes: [LB_NODE, { ...CACHE_NODE, hitRate: 1.0, hitLatency: 2 }, DB_NODE],
      connections: LINKS,
    });
    assert(!allHits.message,                           `No API error (got: ${allHits.message})`);
    assert(allHits.successfulRequests === 5,           `All 5 completed (got ${allHits.successfulRequests})`);
    assert(allHits.failedRequests === 0,               'No dropped requests');
    assert(allHits.averageLatency === 2,               `Avg latency = hitLatency(2) (got ${allHits.averageLatency})`);
    const hitPaths = (allHits.requests ?? []).map(r => r.path?.join('→'));
    assert(hitPaths.every(p => p === 'lb→cache'),      `All paths end at cache (sample: "${hitPaths[0]}")`);
    assert((allHits.nodeMetrics?.db?.processedRequests ?? 0) === 0,
      'DB received 0 requests (all cache hits)');
    assert((allHits.nodeMetrics?.cache?.processedRequests ?? 0) === 5,
      'Cache processed all 5 requests');
    await shot(page, 'cache-16-all-hits-api');

    // ── Test 17: hitRate=0.0 — all requests forwarded to DB ─────────────────
    console.log('\n📋 Test 17: hitRate=0.0 — all requests forwarded to DB');
    const allMisses = await simulate({
      requestCount: 5, entryNodeId: 'lb',
      nodes: [LB_NODE, { ...CACHE_NODE, hitRate: 0.0, hitLatency: 1, latency: 5 }, DB_NODE],
      connections: LINKS,
    });
    assert(!allMisses.message,                             `No API error (got: ${allMisses.message})`);
    assert(allMisses.successfulRequests === 5,             `All 5 completed (got ${allMisses.successfulRequests})`);
    assert(allMisses.averageLatency === 15,                `Avg latency = missLatency(5)+db(10)=15 (got ${allMisses.averageLatency})`);
    const missPaths = (allMisses.requests ?? []).map(r => r.path?.join('→'));
    assert(missPaths.every(p => p === 'lb→cache→db'),      `All paths reach db (sample: "${missPaths[0]}")`);
    assert((allMisses.nodeMetrics?.db?.processedRequests ?? 0) === 5,   'DB received all 5 requests');
    assert((allMisses.nodeMetrics?.cache?.processedRequests ?? 0) === 5,'Cache processed all 5 (miss overhead)');

    // ── Test 18: Determinism — identical results on repeat runs ─────────────
    console.log('\n📋 Test 18: Determinism — same seed produces identical results');
    const body50 = {
      requestCount: 6, entryNodeId: 'lb',
      nodes: [LB_NODE, { ...CACHE_NODE, hitRate: 0.5, hitLatency: 2 }, DB_NODE],
      connections: LINKS,
    };
    const [run1, run2, run3] = await Promise.all([simulate(body50), simulate(body50), simulate(body50)]);
    const pathStr = r => (r.requests ?? []).map(x => x.path?.join('→')).join('|');
    assert(pathStr(run1) === pathStr(run2), `Run 1 == Run 2 ("${pathStr(run1)}")`);
    assert(pathStr(run2) === pathStr(run3), 'Run 2 == Run 3');
    // request-1 has hash=147 → hit at 50% (147 < 500); verify it hits the cache
    const r1byId = (run1.requests ?? []).reduce((m, r) => { m[r.requestId] = r.path?.join('→'); return m }, {});
    assert(r1byId['request-1'] === 'lb→cache', `request-1 deterministically hits cache (hash=147 < 500, got "${r1byId['request-1']}")`);

    // ── Test 19: Latency difference — hit path faster than miss ─────────────
    console.log('\n📋 Test 19: Hit path has lower latency than miss path');
    const latBody = {
      requestCount: 2, entryNodeId: 'lb',
      nodes: [
        LB_NODE,
        { ...CACHE_NODE, hitRate: 0.5, hitLatency: 3, latency: 20 },
        { ...DB_NODE, latency: 15 },
      ],
      connections: LINKS,
    };
    const latRes = await simulate(latBody);
    const latById = (latRes.requests ?? []).reduce((m, r) => { m[r.requestId] = r; return m }, {});
    // request-1 (hash=147) and request-2 (hash=148) both hit at 50%
    assert(latById['request-1']?.totalLatency === 3,
      `request-1 hit latency = hitLatency(3) (got ${latById['request-1']?.totalLatency})`);
    assert(latById['request-1']?.path?.join('→') === 'lb→cache',
      `request-1 path ends at cache (got "${latById['request-1']?.path?.join('→')}")`);

    // ── Test 20: CACHE in UI — build topology and run full round-trip ────────
    console.log('\n📋 Test 20: CACHE in UI — build LB→CACHE→DB topology and simulate');
    await page.goto(BASE, { waitUntil: 'networkidle' });
    await page.click('text=Simple (1-1-1)');
    await waitNodes(page, 3);
    // Delete the SERVICE layer (2nd remove button)
    await page.locator('button[title="Remove"]').nth(1).click();
    await waitNodes(page, 2);
    await page.waitForTimeout(200);
    // Add a new layer
    await page.click('text=+ Add Layer');
    await waitNodes(page, 3);
    await page.waitForTimeout(200);
    // The new layer is at the end; its type select is now the last select on the page
    await page.locator('select').last().selectOption('CACHE');
    await page.waitForTimeout(300);
    // Move CACHE up: it was appended at position 2 (after DB), needs to be at position 1
    await page.locator('button[title="Move up"]').last().click();
    await page.waitForTimeout(300);
    await shot(page, 'cache-20-ui-topology');
    // Check stats
    assert(await page.locator('text=3 nodes').count() > 0, '3 nodes in topology stats');
    assert(await page.locator('text=2 connections').count() > 0, '2 connections shown');
    // Run simulation
    await page.click('text=▶ Run Simulation');
    await page.waitForSelector('text=request-1', { timeout: 10000 });
    await shot(page, 'cache-20-ui-result');
    assert(await page.isVisible('text=request-1'), 'request-1 in results');
    assert(await page.isVisible('text=avg'),        'avg latency shown in header');
    // Verify "cache" appears in the request row (path shows lb→cache or lb→cache→db)
    const reqRowText = await page.locator('button').filter({ hasText: 'request-1' }).textContent();
    assert(reqRowText?.includes('cache'), `request-1 row contains "cache" in path (got: "${reqRowText?.trim()}")`);
    // Click request-1 and verify breakdown
    await page.locator('button').filter({ hasText: 'request-1' }).first().click();
    await page.waitForTimeout(400);
    await shot(page, 'cache-20-ui-breakdown');
    assert(await page.locator('text=cache').count() >= 1, '"cache" visible in breakdown panel');

    // ── Summary ─────────────────────────────────────────────────────────────
    console.log(`\n${'─'.repeat(42)}`);
    console.log(`CACHE Tests: ${passed} passed, ${failed} failed`);
    console.log(`Screenshots: ${SHOTS}`);

  } catch (e) {
    console.error('\n💥 Crash:', e.message);
    console.error(e.stack?.split('\n').slice(0, 5).join('\n'));
    await shot(page, 'cache-crash');
    failed++;
    console.log(`\nResults: ${passed} passed, ${failed} failed`);
  } finally {
    await browser.close();
  }

  process.exit(failed > 0 ? 1 : 0);
})();
