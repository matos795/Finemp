import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { getPresentationSnapshot, getOperationalDre, getOperationalDreEffects } from '../src/lib/api/presentation-client.ts'
import { ApiError } from '../src/lib/api/api-client.ts'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('snapshot preserves exact decimals, null and omitted DRE in one authenticated request', async () => {
  const payload = { partial: true, indicators: { cash: { generation: '99999999999999999.98' }, receivables: { overdueRate: { percent: null, availability: 'UNAVAILABLE_BASE' } } } }
  const abort = new AbortController()
  let calls = 0
  globalThis.fetch = async (path, options) => {
    calls++
    assert.equal(path, '/api/companies/company/financial-reports/presentation-snapshot?month=2026-07&asOf=2026-08-15&scope=COMPETENCE')
    assert.equal(options.credentials, 'include')
    assert.equal(options.cache, 'no-store')
    assert.equal(options.signal, abort.signal)
    return Response.json(payload)
  }
  const result = await getPresentationSnapshot('company', { month: '2026-07', asOf: '2026-08-15', scope: 'COMPETENCE' }, abort.signal)
  assert.deepEqual(result, payload)
  assert.equal(Object.hasOwn(result, 'operationalDre'), false)
  assert.equal(calls, 1)
})

test('effect filters and server pagination remain explicit; company cannot inject a URL path', async () => {
  const response = { items: [{ amount: '-0.01' }], page: 2, size: 1, totalItems: 3, totalPages: 3 }
  globalThis.fetch = async (path) => {
    const url = new URL(path, 'https://example.invalid')
    assert.equal(url.pathname, '/api/companies/a%2Fb%3Fadmin%3D1/financial-reports/operational-dre/effects')
    assert.equal(url.searchParams.get('line'), 'DFIN')
    assert.equal(url.searchParams.get('page'), '2')
    assert.equal(url.searchParams.get('size'), '1')
    return Response.json(response)
  }
  assert.deepEqual(await getOperationalDreEffects('a/b?admin=1', { month: '2026-07', line: 'DFIN', page: 2, size: 1 }), response)
})

test('HTTP failures are propagated without zero reports or retries', async () => {
  for (const status of [400, 401, 403, 404, 500]) {
    let calls = 0
    globalThis.fetch = async () => { calls++; return new Response(null, { status }) }
    await assert.rejects(getPresentationSnapshot('company', { month: '2026-07', asOf: '2026-07-31', scope: 'ALL' }), error => error instanceof ApiError && error.status === status)
    assert.equal(calls, 1)
  }
})

test('cancelled requests remain cancelled rather than falling back to cached financial data', async () => {
  const controller = new AbortController()
  controller.abort()
  globalThis.fetch = async (_path, options) => { options.signal.throwIfAborted() }
  await assert.rejects(getOperationalDreEffects('company', { month: '2026-07', page: 0, size: 20 }, controller.signal), { name: 'AbortError' })
})

test('operational DRE asks one explicit month and keeps lines and subtotals as exact text', async () => {
  const payload = { partial: true, month: '2026-07', lines: [{ line: 'REC', competenceAmount: '0.10', settlementDifference: '0.20', amount: '0.30' }], subtotals: { netResult: '-99999999999999999.99' } }
  const abort = new AbortController()
  let calls = 0
  globalThis.fetch = async (path, options) => {
    calls++
    assert.equal(path, '/api/companies/company/financial-reports/operational-dre?month=2026-07')
    assert.equal(options.cache, 'no-store')
    assert.equal(options.signal, abort.signal)
    return Response.json(payload)
  }
  assert.deepEqual(await getOperationalDre('company', '2026-07', abort.signal), payload)
  assert.equal(calls, 1)
})
