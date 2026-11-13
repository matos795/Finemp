import { afterEach, test } from "node:test"
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

test("cycle report is published with financial-entry permissions", () => {
  const item = findNavigationByPath(
    navigationModules,
    "/reports/working-capital-cycle",
  ).item

  assert.equal(item.id, "ciclo")
  assert.deepEqual(item.permissions, [
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
  ])

  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  assert.match(router, /path="\/reports\/working-capital-cycle"/)
})

test("cycle API sends only inputs and preserves exact backend decimals", async () => {
  let requested = ""
  globalThis.fetch = async (path) => {
    requested = String(path)
    return Response.json({
      from: "2026-09-01",
      to: "2026-11-13",
      asOf: "2026-11-13",
      daysInPeriod: 74,
      pmeDays: 12,
      receivables: {
        volume: "3000.00",
        dailyVolume: "40.540540540541",
        issuedCount: 3,
        grantedDays: "30.00",
        realDays: "28.50",
        averageDelayDays: "2.00",
        punctualityPercent: "90.00",
        earlyPayments: { count: 0, amount: "0.00", averageDays: null },
        interest: "5.00",
        discounts: "0.00",
        cashAmount: "500.00",
        openAmount: "800.00",
        overdueAmount: "100.00",
        openDelayDays: "4.00",
        balanceDays: "19.73",
        bands: [],
        partners: [],
      },
      payables: {
        volume: "2000.00",
        dailyVolume: "27.027027027027",
        issuedCount: 2,
        grantedDays: "25.00",
        realDays: "26.00",
        averageDelayDays: "1.00",
        punctualityPercent: "80.00",
        earlyPayments: { count: 0, amount: "0.00", averageDays: null },
        interest: "0.00",
        discounts: "3.00",
        cashAmount: "0.00",
        openAmount: "400.00",
        overdueAmount: "0.00",
        openDelayDays: null,
        balanceDays: "14.80",
        bands: [],
        partners: [],
      },
      pmrDays: "28.50",
      pmpDays: "26.00",
      operatingCycleDays: "40.50",
      financialCycleDays: "14.50",
      workingCapitalForCycle: "587.84",
    })
  }

  const report = await getWorkingCapitalCycle("company-1", 3, 12)

  assert.equal(
    requested,
    "/api/companies/company-1/financial-reports/working-capital-cycle?months=3&pmeDays=12",
  )
  assert.equal(report.workingCapitalForCycle, "587.84")
  assert.equal(typeof report.workingCapitalForCycle, "string")
})

test("cycle page presents backend metrics without financial recomputation", () => {
  const page = readFileSync(
    new URL("../src/features/reports/pages/working-capital-cycle-page.tsx", import.meta.url),
    "utf8",
  )

  assert.doesNotMatch(page, /pmrDays\s*[+-]/)
  assert.doesNotMatch(page, /pmpDays\s*[+-]/)
  assert.doesNotMatch(page, /dailyVolume\s*[*]/)
  assert.doesNotMatch(page, /workingCapitalForCycle\s*=/)
  assert.match(page, /PME informado vale[\s\S]*somente para esta análise/)
})
