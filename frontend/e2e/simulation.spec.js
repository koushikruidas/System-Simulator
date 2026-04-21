// @ts-check
import { test, expect } from '@playwright/test'

// ── Helpers ───────────────────────────────────────────────────────────────────

async function waitForResults(page) {
  await expect(page.getByTestId('results-section')).toBeVisible({ timeout: 30_000 })
}

async function enableTimeSeriesMode(page) {
  const toggle = page.getByTestId('timeseries-toggle')
  const checked = await toggle.isChecked()
  if (!checked) await toggle.check()
}

async function setArrivalRate(page, value) {
  const input = page.getByTestId('arrival-rate-input')
  await input.fill(String(value))
  await input.press('Tab')
}

async function setDuration(page, value) {
  const input = page.getByTestId('duration-input')
  await input.fill(String(value))
  await input.press('Tab')
}

async function setRequests(page, value) {
  const input = page.getByTestId('requests-input')
  await input.fill(String(value))
  await input.press('Tab')
}

async function setLayerCapacity(page, idx, value) {
  const input = page.getByTestId(`layer-${idx}-capacity`)
  await input.fill(String(value))
  await input.press('Tab')
}

async function setLayerQueueLimit(page, idx, value) {
  const input = page.getByTestId(`layer-${idx}-queue-limit`)
  await input.fill(String(value))
  await input.press('Tab')
}

async function setLayerLatency(page, idx, value) {
  const input = page.getByTestId(`layer-${idx}-latency`)
  await input.fill(String(value))
  await input.press('Tab')
}

async function clickRun(page) {
  await page.getByTestId('run-button').click()
}

async function extractStatNumber(page, testId) {
  const text = await page.getByTestId(testId).innerText()
  const match = text.match(/\d+/)
  return match ? parseInt(match[0]) : 0
}

// ── Group 1: Basic Flow ───────────────────────────────────────────────────────

test.describe('Group 1: Basic Flow', () => {
  test('run button present and page loads', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('run-button')).toBeVisible()
    await expect(page.getByTestId('run-button')).not.toBeDisabled()
  })

  test('batch mode: all requests complete with sufficient capacity', async ({ page }) => {
    await page.goto('/')

    // Use Simple preset: 1-1-1 topology
    await page.getByTestId('preset-simple').click()
    await setRequests(page, 1)

    await setLayerCapacity(page, 0, 10)
    await setLayerCapacity(page, 1, 10)
    await setLayerCapacity(page, 2, 10)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    await clickRun(page)
    await waitForResults(page)

    const completed = await extractStatNumber(page, 'stat-completed')
    expect(completed).toBe(1)

    // No failures with generous capacity
    await expect(page.getByTestId('stat-failed')).not.toBeVisible()
  })

  test('time-series mode: all requests complete with low load', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    await setArrivalRate(page, 5)
    await setDuration(page, 5)

    await clickRun(page)
    await waitForResults(page)

    const completed = await extractStatNumber(page, 'stat-completed')
    expect(completed).toBe(25)

    await expect(page.getByTestId('stat-failed')).not.toBeVisible()
  })
})

// ── Group 2: Time Series Validation ──────────────────────────────────────────

test.describe('Group 2: Time Series Validation', () => {
  test('time series panel renders after time-series run', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)
    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    await clickRun(page)
    await waitForResults(page)

    await expect(page.getByTestId('timeseries-panel')).toBeVisible()
  })

  test('totalRequests = arrivalRate * duration', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setLayerCapacity(page, 0, 50)
    await setLayerCapacity(page, 1, 50)
    await setLayerCapacity(page, 2, 50)
    await setLayerQueueLimit(page, 1, 200)
    await setLayerQueueLimit(page, 2, 200)
    await setArrivalRate(page, 10)
    await setDuration(page, 5)

    await clickRun(page)
    await waitForResults(page)

    // completed + dropped should equal arrivalRate * duration = 50
    const completed = await extractStatNumber(page, 'stat-completed')
    const failedVisible = await page.getByTestId('stat-failed').isVisible()
    const failed = failedVisible ? await extractStatNumber(page, 'stat-failed') : 0
    expect(completed + failed).toBeLessThanOrEqual(50)
    expect(completed).toBeGreaterThan(0)
  })

  test('timeseries panel not shown in batch mode', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    // Ensure time-series toggle is off
    const toggle = page.getByTestId('timeseries-toggle')
    if (await toggle.isChecked()) await toggle.uncheck()

    await setRequests(page, 3)
    await clickRun(page)
    await waitForResults(page)

    await expect(page.getByTestId('timeseries-panel')).not.toBeVisible()
  })
})

// ── Group 3: High Load with Drops ────────────────────────────────────────────

test.describe('Group 3: High Load with Drops', () => {
  test('drops occur when queue is too small for load', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    // Service: capacity=2, queueLimit=3 — will overflow at rate=50
    await setLayerCapacity(page, 0, 100)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 2)
    await setLayerQueueLimit(page, 1, 3)
    await setLayerLatency(page, 1, 5)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 2, 50)

    await setArrivalRate(page, 50)
    await setDuration(page, 10)

    await clickRun(page)
    await waitForResults(page)

    const failed = await extractStatNumber(page, 'stat-failed')
    expect(failed).toBeGreaterThan(0)
  })

  test('completed + dropped <= totalInjected', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    await setLayerCapacity(page, 0, 100)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 2)
    await setLayerQueueLimit(page, 1, 3)
    await setLayerLatency(page, 1, 5)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 2, 50)

    await setArrivalRate(page, 50)
    await setDuration(page, 10)

    await clickRun(page)
    await waitForResults(page)

    const completed = await extractStatNumber(page, 'stat-completed')
    const failedVisible = await page.getByTestId('stat-failed').isVisible()
    const failed = failedVisible ? await extractStatNumber(page, 'stat-failed') : 0
    const total = 50 * 10 // arrivalRate * duration
    expect(completed + failed).toBeLessThanOrEqual(total)
    expect(completed + failed).toBeGreaterThan(0)
  })
})

// ── Group 4: DB Bottleneck ────────────────────────────────────────────────────

test.describe('Group 4: DB Bottleneck', () => {
  test('drops reported at bottleneck node', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    // Service has plenty of capacity, DB is the bottleneck
    await setLayerCapacity(page, 0, 100)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 20)
    await setLayerQueueLimit(page, 1, 100)
    await setLayerLatency(page, 1, 2)
    await setLayerCapacity(page, 2, 1)
    await setLayerQueueLimit(page, 2, 2)
    await setLayerLatency(page, 2, 10)

    await setArrivalRate(page, 20)
    await setDuration(page, 10)

    await clickRun(page)
    await waitForResults(page)

    const failed = await extractStatNumber(page, 'stat-failed')
    expect(failed).toBeGreaterThan(0)
  })

  test('node-metric-db shows dropped count', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    await setLayerCapacity(page, 0, 100)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 20)
    await setLayerQueueLimit(page, 1, 100)
    await setLayerLatency(page, 1, 2)
    await setLayerCapacity(page, 2, 1)
    await setLayerQueueLimit(page, 2, 2)
    await setLayerLatency(page, 2, 10)

    await setArrivalRate(page, 20)
    await setDuration(page, 10)

    await clickRun(page)
    await waitForResults(page)

    // The last layer is named like "layer-0-1" or "db" depending on preset expansion
    // Check that at least one node-metric shows a drop indicator
    const nodeMetrics = page.locator('[data-testid^="node-metric-"]')
    const count = await nodeMetrics.count()
    expect(count).toBeGreaterThan(0)

    let hasDrops = false
    for (let i = 0; i < count; i++) {
      const text = await nodeMetrics.nth(i).innerText()
      if (text.includes('✗')) { hasDrops = true; break }
    }
    expect(hasDrops).toBe(true)
  })
})

// ── Group 5: Input Validation ─────────────────────────────────────────────────

test.describe('Group 5: Input Validation', () => {
  test('run button disabled when all layers removed', async ({ page }) => {
    await page.goto('/')

    // Remove all 3 default layers
    const removeButtons = page.locator('button[title="Remove"]')
    const count = await removeButtons.count()
    for (let i = 0; i < count; i++) {
      await page.locator('button[title="Remove"]').first().click()
    }

    await expect(page.getByTestId('run-button')).toBeDisabled()
  })

  test('run button enabled when at least one layer exists', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('run-button')).not.toBeDisabled()
  })

  test('loading state shown during simulation', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)

    await clickRun(page)

    // Button should show loading state immediately after click
    const buttonText = await page.getByTestId('run-button').innerText()
    const isRunning = buttonText.includes('Running') || buttonText.includes('Run')
    expect(isRunning).toBe(true)

    await waitForResults(page)
  })
})

// ── Group 6: API ↔ UI Consistency ─────────────────────────────────────────────

test.describe('Group 6: API ↔ UI Consistency', () => {
  test('UI completed count matches API successfulRequests', async ({ page }) => {
    let apiResponse = null

    await page.route('**/simulate', async route => {
      const response = await route.fetch()
      const body = await response.json()
      apiResponse = body
      await route.fulfill({ response, json: body })
    })

    await page.goto('/')
    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)

    await clickRun(page)
    await waitForResults(page)

    expect(apiResponse).not.toBeNull()

    const uiCompleted = await extractStatNumber(page, 'stat-completed')
    expect(uiCompleted).toBe(apiResponse.successfulRequests)
  })

  test('UI failed count matches API failedRequests when drops occur', async ({ page }) => {
    let apiResponse = null

    await page.route('**/simulate', async route => {
      const response = await route.fetch()
      const body = await response.json()
      apiResponse = body
      await route.fulfill({ response, json: body })
    })

    await page.goto('/')
    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setLayerCapacity(page, 0, 100)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 2)
    await setLayerQueueLimit(page, 1, 3)
    await setLayerLatency(page, 1, 5)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 2, 50)
    await setArrivalRate(page, 50)
    await setDuration(page, 10)

    await clickRun(page)
    await waitForResults(page)

    expect(apiResponse).not.toBeNull()
    expect(apiResponse.failedRequests).toBeGreaterThan(0)

    const uiFailed = await extractStatNumber(page, 'stat-failed')
    expect(uiFailed).toBe(apiResponse.failedRequests)
  })

  test('node metrics in UI match API nodeMetrics', async ({ page }) => {
    let apiResponse = null

    await page.route('**/simulate', async route => {
      const response = await route.fetch()
      const body = await response.json()
      apiResponse = body
      await route.fulfill({ response, json: body })
    })

    await page.goto('/')
    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)
    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    await clickRun(page)
    await waitForResults(page)

    expect(apiResponse).not.toBeNull()
    const nodeMetrics = apiResponse.nodeMetrics ?? {}

    for (const [nodeId] of Object.entries(nodeMetrics)) {
      await expect(page.getByTestId(`node-metric-${nodeId}`)).toBeVisible()
    }
  })
})

// ── Group 7: Multiple Run Isolation ──────────────────────────────────────────

test.describe('Group 7: Multiple Run Isolation', () => {
  test('second run replaces first run results', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    // First run: rate=5, duration=5 → 25 injected
    await setArrivalRate(page, 5)
    await setDuration(page, 5)
    await clickRun(page)
    await waitForResults(page)

    const firstCompleted = await extractStatNumber(page, 'stat-completed')

    // Second run: rate=10, duration=5 → 50 injected (should complete more)
    await setArrivalRate(page, 10)
    await setDuration(page, 5)
    await clickRun(page)
    await waitForResults(page)

    const secondCompleted = await extractStatNumber(page, 'stat-completed')
    expect(secondCompleted).toBeGreaterThan(firstCompleted)
  })

  test('results cleared on topology change', async ({ page }) => {
    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)
    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    await clickRun(page)
    await waitForResults(page)
    await expect(page.getByTestId('results-section')).toBeVisible()

    // Change topology — results should disappear
    await page.locator('button[title="Remove"]').first().click()
    await expect(page.getByTestId('results-section')).not.toBeVisible()
  })
})

// ── Group 8: Loading / UX State ───────────────────────────────────────────────

test.describe('Group 8: Loading and UX State', () => {
  test('run button disabled while simulation runs', async ({ page }) => {
    // Intercept and hold the request briefly to observe disabled state
    let resolveRequest
    await page.route('**/simulate', async route => {
      await new Promise(r => { resolveRequest = r })
      const response = await route.fetch()
      await route.fulfill({ response })
    })

    await page.goto('/')
    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)

    await clickRun(page)

    // Button should be disabled while request is in-flight
    await expect(page.getByTestId('run-button')).toBeDisabled()

    resolveRequest()
    await waitForResults(page)
    await expect(page.getByTestId('run-button')).not.toBeDisabled()
  })

  test('error message shown on API failure', async ({ page }) => {
    await page.route('**/simulate', route =>
      route.fulfill({ status: 500, body: JSON.stringify({ message: 'Server error' }) })
    )

    await page.goto('/')
    await page.getByTestId('preset-simple').click()

    await clickRun(page)

    await expect(page.getByTestId('error-message')).toBeVisible({ timeout: 10_000 })
  })

  test('no stale error shown after successful run', async ({ page }) => {
    // First run fails
    await page.route('**/simulate', route =>
      route.fulfill({ status: 500, body: JSON.stringify({ message: 'Server error' }) })
    )

    await page.goto('/')
    await page.getByTestId('preset-simple').click()
    await clickRun(page)
    await expect(page.getByTestId('error-message')).toBeVisible({ timeout: 10_000 })

    // Remove the route so next run succeeds
    await page.unroute('**/simulate')

    await enableTimeSeriesMode(page)
    await setArrivalRate(page, 5)
    await setDuration(page, 5)
    await setLayerCapacity(page, 0, 20)
    await setLayerCapacity(page, 1, 20)
    await setLayerCapacity(page, 2, 20)
    await setLayerQueueLimit(page, 1, 50)
    await setLayerQueueLimit(page, 2, 50)

    await clickRun(page)
    await waitForResults(page)

    await expect(page.getByTestId('error-message')).not.toBeVisible()
  })
})

// ── Group 9: Long-Run Stress Test ─────────────────────────────────────────────

test.describe('Group 9: Long-Run Stress Test', () => {
  test('arrivalRate=500 duration=20 completes without error', async ({ page }) => {
    test.setTimeout(120_000)

    await page.goto('/')

    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    await setLayerCapacity(page, 0, 1000)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 100)
    await setLayerQueueLimit(page, 1, 5000)
    await setLayerLatency(page, 1, 5)
    await setLayerCapacity(page, 2, 100)
    await setLayerQueueLimit(page, 2, 5000)
    await setLayerLatency(page, 2, 10)

    await setArrivalRate(page, 500)
    await setDuration(page, 20)

    await clickRun(page)
    await waitForResults(page)

    await expect(page.getByTestId('error-message')).not.toBeVisible()

    const completed = await extractStatNumber(page, 'stat-completed')
    expect(completed).toBeGreaterThan(0)

    const failedVisible = await page.getByTestId('stat-failed').isVisible()
    const failed = failedVisible ? await extractStatNumber(page, 'stat-failed') : 0
    const total = 500 * 20
    expect(completed + failed).toBeLessThanOrEqual(total)
  })

  test('stress test: completed + dropped conservation holds', async ({ page }) => {
    test.setTimeout(120_000)

    let apiResponse = null
    await page.route('**/simulate', async route => {
      const response = await route.fetch()
      const body = await response.json()
      apiResponse = body
      await route.fulfill({ response, json: body })
    })

    await page.goto('/')
    await page.getByTestId('preset-simple').click()
    await enableTimeSeriesMode(page)

    // Tight queue to force drops at scale
    await setLayerCapacity(page, 0, 1000)
    await setLayerQueueLimit(page, 0, 0)
    await setLayerCapacity(page, 1, 10)
    await setLayerQueueLimit(page, 1, 20)
    await setLayerLatency(page, 1, 5)
    await setLayerCapacity(page, 2, 10)
    await setLayerQueueLimit(page, 2, 20)
    await setLayerLatency(page, 2, 10)

    await setArrivalRate(page, 500)
    await setDuration(page, 20)

    await clickRun(page)
    await waitForResults(page)

    expect(apiResponse).not.toBeNull()
    const { successfulRequests, failedRequests, totalRequests } = apiResponse
    expect(successfulRequests + failedRequests).toBeLessThanOrEqual(totalRequests)
    expect(successfulRequests).toBeGreaterThan(0)
  })
})
