import { test } from "node:test"
import assert from "node:assert/strict"
import {
  canSeeFinancialDashboard,
  dashboardMonthCash,
  dashboardQuery,
  describeDashboardError,
  formatIndicatorPercent,
  overdueSummary,
} from "../src/features/dashboard/lib/financial-dashboard.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const can = (...permissions) => (permission) => permissions.includes(permission)

const aging = (overdueCount, overdueAmount) => ({
  asOf: "2026-11-11", direction: "INCOME", scope: "ALL",
  totalCount: 5, totalAmount: "5000.00", overdueCount, overdueAmount,
  dueWithin30Count: 1, dueWithin30Amount: "100.00", bands: [],
})

const month = (n, values = {}) => ({ month: n, realIncome: "0.00", realExpense: "0.00", openIncome: "0.00", openExpense: "0.00", ...values })

const snapshot = (overrides = {}) => ({
  schemaVersion: 1,
  partial: true,
  companyId: "c1",
  month: "2026-11",
  asOf: "2026-11-11",
  scope: "ALL",
  cashMonth: { month: "2026-11", days: [] },
  cashYear: {
    year: 2026,
    months: Array.from({ length: 12 }, (_, i) => month(i + 1, i === 10 ? { realIncome: "1200.50", realExpense: "800.00", openIncome: "300.00", openExpense: "99.99" } : {})),
    activityMonths: [], quarters: [], halfYears: [],
    totalRealIncome: "0", totalRealExpense: "0", totalOpenIncome: "0", totalOpenExpense: "0",
  },
  payables: { ...aging(0, "0.00"), direction: "EXPENSE" },
  receivables: aging(2, "450.00"),
  indicators: {
    percentUnit: "PERCENT",
    percentDecimalPlaces: 1,
    cash: { realizedIncome: "1200.50", realizedExpense: "800.00", generation: "400.50", margin: { numerator: "400.50", denominator: "1200.50", availability: "AVAILABLE", percent: "33.4" } },
    receivables: { overdueRate: { numerator: "450.00", denominator: "5000.00", availability: "AVAILABLE", percent: "9.0" } },
  },
  ...overrides,
})

test("query is explicit: chosen month, today as aging base and ALL scope", () => {
  assert.deepEqual(dashboardQuery("2026-11-11"), { month: "2026-11", asOf: "2026-11-11", scope: "ALL" })
  assert.deepEqual(dashboardQuery("2026-11-11", "2026-08"), { month: "2026-08", asOf: "2026-11-11", scope: "ALL" })
})

test("month cash comes from the backend month of cashYear, without summing days", () => {
  assert.deepEqual(dashboardMonthCash(snapshot()), {
    month: 11, realIncome: "1200.50", realExpense: "800.00", openIncome: "300.00", openExpense: "99.99",
  })
  assert.equal(dashboardMonthCash(snapshot({ month: "2026-13" })), null)
})

test("percentages are shown as provided and unavailable bases are not turned into zero", () => {
  assert.equal(formatIndicatorPercent({ availability: "AVAILABLE", percent: "33.4", numerator: "1", denominator: "3" }), "33,4%")
  assert.equal(formatIndicatorPercent({ availability: "AVAILABLE", percent: "-120.0", numerator: "1", denominator: "3" }), "-120,0%")
  assert.equal(formatIndicatorPercent({ availability: "UNAVAILABLE_BASE", percent: null, numerator: "0", denominator: "0" }), null)
  assert.equal(formatIndicatorPercent(undefined), null)
})

test("overdue alert uses backend counts and amounts only when there is something overdue", () => {
  assert.deepEqual(overdueSummary(snapshot()), {
    payables: null,
    receivables: { count: 2, amount: "450.00" },
  })
  assert.equal(overdueSummary(snapshot({ receivables: aging(0, "0.00") })), null)
})

test("the financial panel requires FINANCIAL_ENTRY_VIEW or MANAGE, never a role", () => {
  assert.equal(canSeeFinancialDashboard(can("FINANCIAL_ENTRY_VIEW")), true)
  assert.equal(canSeeFinancialDashboard(can("FINANCIAL_ENTRY_MANAGE")), true)
  assert.equal(canSeeFinancialDashboard(can("DASHBOARD_VIEW", "DRE_VIEW", "BANK_ACCOUNT_MANAGE")), false)
  assert.equal(canSeeFinancialDashboard(() => false), false)
})

test("snapshot errors map to neutral messages, without zero values", () => {
  assert.match(describeDashboardError(new ApiError(400, "x")), /mês/)
  assert.match(describeDashboardError(new ApiError(403, "x")), /não tem acesso/)
  assert.match(describeDashboardError(new ApiError(404, "x")), /não está disponível neste ambiente/)
  assert.match(describeDashboardError(new ApiError(503, "x")), /Não foi possível carregar/)
  assert.match(describeDashboardError(new TypeError("offline")), /Não foi possível carregar/)
})
