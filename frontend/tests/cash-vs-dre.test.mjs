import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { getCashVsOperationalDre } from "../src/features/reports/api/reports-api.ts"
import {
  findNavigationByPath,
  navigationModules,
  visibleNavigation,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

const can = (...permissions) => (permission) => permissions.includes(permission)
const none = () => false

test("cash versus DRE is published only with financial read access", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/reports/pages/cash-vs-operational-dre-page.tsx", import.meta.url),
    "utf8",
  )

  const item = findNavigationByPath(navigationModules, "/reports/cash-vs-dre").item
  assert.equal(item.id, "conc")
  assert.deepEqual(item.permissions, [
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
  ])

  const visible = visibleNavigation(
    navigationModules,
    can("FINANCIAL_ENTRY_VIEW"),
    none,
  ).flatMap((module) => module.items.map((candidate) => candidate.id))
  assert.ok(visible.includes("conc"))

  assert.match(router, /path="\/reports\/cash-vs-dre"/)
  assert.match(router, /<CashVsOperationalDrePage \/>/)
  assert.match(page, /Não é[\s\S]*conciliação contábil completa/)
  assert.match(page, /data\.difference/)
  assert.doesNotMatch(page, /netCashGeneration\s*[-+]\s*/)
})

test("cash versus DRE API sends only company and selected month", async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      companyId: "company-1",
      month: "2026-11",
      partial: true,
      coverage: ["FINANCIAL_ENTRIES"],
      cash: {
        realizedIncome: "1800.00",
        realizedExpense: "700.00",
        netCashGeneration: "1100.00",
      },
      operationalDre: { netResult: "1000.00" },
      difference: "100.00",
    })
  }

  const result = await getCashVsOperationalDre("company-1", "2026-11")

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/financial-reports/cash-vs-dre?month=2026-11",
  )
  assert.equal(calls[0].options.credentials, "include")
  assert.equal(result.difference, "100.00")
})
