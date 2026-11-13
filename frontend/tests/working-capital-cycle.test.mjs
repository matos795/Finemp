import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { getWorkingCapitalCycle } from "../src/features/reports/api/reports-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

test("Ciclo financeiro is published with existing financial permissions", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/reports/pages/working-capital-cycle-page.tsx", import.meta.url),
    "utf8",
  )

  const item = findNavigationByPath(
    navigationModules,
    "/reports/working-capital-cycle",
  ).item

  assert.equal(item.id, "ciclo")
  assert.deepEqual(item.permissions, [
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
  ])
  assert.match(
    router,
    /path="\/reports\/working-capital-cycle"[\s\S]*?<WorkingCapitalCyclePage/,
  )
  assert.match(page, /getWorkingCapitalCycle/)
  assert.doesNotMatch(page, /receivablesDays\s*[+\-*/]|payablesDays\s*[+\-*/]/)
  assert.doesNotMatch(page, /Number\(.*receivables|parseFloat/)
})

test("cycle API sends PME only when explicitly informed", async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      from: "2026-11-01",
      to: "2026-11-30",
      periodDays: 30,
      receivableTitles: 0,
      payableTitles: 0,
      receivablesDays: null,
      payablesDays: null,
      inventoryDays: null,
      operatingCycleDays: null,
      financialCycleDays: null,
      dailyRevenue: "0.00",
      cycleWorkingCapital: null,
    })
  }

  await getWorkingCapitalCycle("company-1", "2026-11-01", "2026-11-30")
  await getWorkingCapitalCycle("company-1", "2026-11-01", "2026-11-30", "7,50")

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/financial-reports/working-capital-cycle?from=2026-11-01&to=2026-11-30",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/financial-reports/working-capital-cycle?from=2026-11-01&to=2026-11-30&inventoryDays=7.50",
  )
})
