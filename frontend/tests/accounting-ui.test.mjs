import { afterEach, test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  classifyAccountingAccount,
  listAccountingAccounts,
  listAccountingMovements,
} from "../src/features/accounting/api/accounting-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("accounting account list and classification use the official Company endpoints", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "csrf-token",
      })
    }

    if (options.method === "PUT") {
      return new Response(null, { status: 204 })
    }

    return Response.json([])
  }

  await listAccountingAccounts("company-1")
  await classifyAccountingAccount(
    "company-1",
    "account-1",
    "classification-1",
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/accounting-accounts/classifications",
  )
  assert.equal(
    calls[2].path,
    "/api/companies/company-1/accounting-accounts/account-1/classification",
  )
  assert.deepEqual(JSON.parse(calls[2].options.body), {
    classificationId: "classification-1",
  })
})

test("accounting movements remain a period-scoped read-only query", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    return Response.json([])
  }

  await listAccountingMovements(
    "company-1",
    "2026-10-01",
    "2026-10-31",
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/accounting-movements?startDate=2026-10-01&endDate=2026-10-31",
  )
  assert.equal(calls[0].options.method, undefined)
})

test("accounting screens preserve the read/write boundary", () => {
  const accounts = readFileSync(
    new URL("../src/features/accounting/pages/accounting-accounts-page.tsx", import.meta.url),
    "utf8",
  )
  const movements = readFileSync(
    new URL("../src/features/accounting/pages/accounting-movements-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(accounts, /ACCOUNT_CLASSIFICATION_MANAGE/)
  assert.match(accounts, /classifyAccountingAccount/)
  assert.match(movements, /somente leitura/)
  assert.doesNotMatch(movements, /POST|PUT|DELETE|classifyAccountingAccount/)
})
