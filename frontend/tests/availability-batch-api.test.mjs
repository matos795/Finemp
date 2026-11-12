import { afterEach, test } from "node:test"
import assert from "node:assert/strict"
import {
  createResultCenter,
  updateResultCenter,
} from "../src/features/result-centers/api/result-center-api.ts"
import {
  closeFinancialPeriod,
  getFinancialPeriod,
  reopenFinancialPeriod,
} from "../src/features/financial-periods/api/financial-period-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function installMutationFetch(calls) {
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "csrf-token",
      })
    }

    return Response.json({
      id: "resource-1",
      companyId: "company-1",
      code: "1",
      name: "Comercial",
      active: true,
      hasChildren: false,
      version: 4,
      type: "ANALYTICAL",
      commandId: "11111111-1111-4111-8111-111111111111",
      periodId: "period-1",
      period: "2026-11",
      closed: true,
      auditEventId: "audit-1",
    })
  }
}

test("result center writes preserve backend version contract", async () => {
  const calls = []
  installMutationFetch(calls)

  await createResultCenter("company-1", {
    code: "1",
    name: "Comercial",
    type: "ANALYTICAL",
  })

  await updateResultCenter("company-1", "center-1", {
    name: "Comercial atualizado",
    active: false,
    expectedVersion: 3,
    type: "GROUP",
  })

  assert.equal(calls[1].path, "/api/companies/company-1/result-centers")
  assert.deepEqual(JSON.parse(calls[1].options.body), {
    code: "1",
    name: "Comercial",
    type: "ANALYTICAL",
  })

  assert.equal(calls[3].path, "/api/companies/company-1/result-centers/center-1")
  assert.deepEqual(JSON.parse(calls[3].options.body), {
    name: "Comercial atualizado",
    active: false,
    expectedVersion: 3,
    type: "GROUP",
  })
})

test("period commands send idempotency key, version and reopen reason", async () => {
  const calls = []
  installMutationFetch(calls)

  const closeId = "11111111-1111-4111-8111-111111111111"
  const reopenId = "22222222-2222-4222-8222-222222222222"

  await closeFinancialPeriod("company-1", "2026-11", null, closeId)
  await reopenFinancialPeriod(
    "company-1",
    "2026-11",
    4,
    "Correção controlada",
    reopenId,
  )

  assert.equal(
    calls[1].path,
    "/api/companies/company-1/financial-periods/2026-11/closures",
  )
  assert.equal(
    new Headers(calls[1].options.headers).get("Idempotency-Key"),
    closeId,
  )
  assert.deepEqual(JSON.parse(calls[1].options.body), {
    expectedVersion: null,
  })

  assert.equal(
    calls[3].path,
    "/api/companies/company-1/financial-periods/2026-11/reopenings",
  )
  assert.equal(
    new Headers(calls[3].options.headers).get("Idempotency-Key"),
    reopenId,
  )
  assert.deepEqual(JSON.parse(calls[3].options.body), {
    expectedVersion: 4,
    reason: "Correção controlada",
  })
})


test("financial period lookup returns the exact version used by closure", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/companies/company-1/financial-periods/2026-10") {
      return Response.json({
        id: "period-10",
        companyId: "company-1",
        period: "2026-10",
        closed: false,
        version: 7,
        updatedBy: "user-1",
        updatedAt: "2026-11-12T18:00:00Z",
      })
    }

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "csrf-token",
      })
    }

    return Response.json({
      companyId: "company-1",
      commandId: "33333333-3333-4333-8333-333333333333",
      periodId: "period-10",
      period: "2026-10",
      closed: true,
      version: 8,
      auditEventId: "audit-10",
    })
  }

  const state = await getFinancialPeriod("company-1", "2026-10")
  await closeFinancialPeriod(
    "company-1",
    "2026-10",
    state.version,
    "33333333-3333-4333-8333-333333333333",
  )

  assert.equal(state.version, 7)
  assert.equal(
    calls[0].path,
    "/api/companies/company-1/financial-periods/2026-10",
  )
  assert.deepEqual(JSON.parse(calls[2].options.body), {
    expectedVersion: 7,
  })
})
