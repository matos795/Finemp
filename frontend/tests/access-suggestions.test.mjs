import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import {
  ACCOUNTANT_SUGGESTION,
  CLIENT_FINANCE_SUGGESTION,
} from "../src/features/platform-admin/lib/access-suggestions.ts"
import { replaceCompanyAccess } from "../src/features/platform-admin/api/platform-admin-api.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test("client finance suggestion can set up accounts, partners and the category structure", () => {
  for (const permission of [
    "BANK_ACCOUNT_VIEW",
    "BANK_ACCOUNT_MANAGE",
    "BUSINESS_PARTNER_VIEW",
    "BUSINESS_PARTNER_MANAGE",
    "FINANCIAL_CATEGORY_VIEW",
    "FINANCIAL_CATEGORY_STRUCTURE_MANAGE",
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
  ]) {
    assert.ok(CLIENT_FINANCE_SUGGESTION.includes(permission), permission)
  }
})

test("client finance suggestion keeps the category classification (cash/DRE/cost) out (#331)", () => {
  assert.equal(CLIENT_FINANCE_SUGGESTION.includes("FINANCIAL_CATEGORY_MANAGE"), false)
  assert.ok(CLIENT_FINANCE_SUGGESTION.includes("FINANCIAL_CATEGORY_STRUCTURE_MANAGE"))
  assert.ok(CLIENT_FINANCE_SUGGESTION.includes("BUSINESS_PARTNER_MANAGE"))
})

test("accountant suggestion is unchanged by the category split", () => {
  assert.ok(ACCOUNTANT_SUGGESTION.includes("FINANCIAL_CATEGORY_VIEW"))
  assert.equal(ACCOUNTANT_SUGGESTION.includes("FINANCIAL_CATEGORY_STRUCTURE_MANAGE"), false)
  assert.equal(ACCOUNTANT_SUGGESTION.includes("FINANCIAL_CATEGORY_MANAGE"), false)
})

test("client finance suggestion does not add center, accounting, period or firm management", () => {
  assert.ok(CLIENT_FINANCE_SUGGESTION.includes("RESULT_CENTER_VIEW"))
  for (const permission of [
    "RESULT_CENTER_MANAGE",
    "ACCOUNT_CLASSIFICATION_MANAGE",
    "MANUAL_ENTRY_MANAGE",
    "QUESTOR_SYNC",
    "FINANCIAL_PERIOD_CLOSE",
    "FINANCIAL_PERIOD_REOPEN",
  ]) {
    assert.equal(CLIENT_FINANCE_SUGGESTION.includes(permission), false, permission)
  }
  assert.equal(new Set(CLIENT_FINANCE_SUGGESTION).size, CLIENT_FINANCE_SUGGESTION.length)
  assert.equal(ACCOUNTANT_SUGGESTION.includes("FINANCIAL_CATEGORY_MANAGE"), false)
})

test("planning permissions stay explicit instead of changing role suggestions", () => {
  for (const permission of ["PLANNING_VIEW", "PLANNING_MANAGE"]) {
    assert.equal(
      ACCOUNTANT_SUGGESTION.includes(permission),
      false,
      `accountant/${permission}`,
    )
    assert.equal(
      CLIENT_FINANCE_SUGGESTION.includes(permission),
      false,
      `client/${permission}`,
    )
  }
})

test("the CLIENT role grants nothing by itself: only the explicit list is sent", async () => {
  const bodies = []
  globalThis.fetch = async (path, options = {}) => {
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    bodies.push(JSON.parse(options.body))
    return new Response(null, { status: 204 })
  }

  await replaceCompanyAccess("f1", "c1", "co1", "u1", "CLIENT", [])
  await replaceCompanyAccess("f1", "c1", "co1", "u1", "CLIENT", ["FINANCIAL_ENTRY_VIEW"])

  assert.deepEqual(bodies[0].permissions, [])
  assert.deepEqual(bodies[1].permissions, ["FINANCIAL_ENTRY_VIEW"])
})
