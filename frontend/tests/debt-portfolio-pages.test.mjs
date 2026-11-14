import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  getDebtPortfolioOverview,
  getDebtPortfolioSchedule,
} from "../src/features/debt/api/debts-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("debt overview and schedule are real permission-guarded routes", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  for (const [path, id, component] of [
    ["/debts/overview", "divPainel", "DebtPortfolioOverviewPage"],
    ["/debts/schedule", "divCron", "DebtPortfolioSchedulePage"],
  ]) {
    const item = findNavigationByPath(navigationModules, path).item
    assert.equal(item.id, id)
    assert.deepEqual(item.permissions, ["DEBT_VIEW", "DEBT_MANAGE"])
    assert.match(
      router,
      new RegExp(
        `path="${path}"[\\s\\S]*?DEBT_VIEW[\\s\\S]*?DEBT_MANAGE[\\s\\S]*?<${component}`,
      ),
    )
  }
})

test("debt portfolio reads keep overview, month and pagination on the server", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })

    if (String(path).endsWith("/overview")) {
      return Response.json({
        companyId: "company-1",
        agreementCount: 0,
        principalAmount: "0.00",
        scheduledAmount: "0.00",
        interestAmount: "0.00",
        byOrigin: [],
      })
    }

    return Response.json({
      items: [],
      page: 2,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await getDebtPortfolioOverview("company-1")
  await getDebtPortfolioSchedule("company-1", "2026-11", 2)

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/debts/overview",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/debts/schedule?month=2026-11&page=2&size=50",
  )
  assert.equal(calls[0].options.cache, "no-store")
  assert.equal(calls[1].options.cache, "no-store")
})

test("debt portfolio pages only format backend monetary strings", () => {
  const overview = readFileSync(
    new URL("../src/features/debt/pages/debt-portfolio-overview-page.tsx", import.meta.url),
    "utf8",
  )
  const schedule = readFileSync(
    new URL("../src/features/debt/pages/debt-portfolio-schedule-page.tsx", import.meta.url),
    "utf8",
  )

  for (const source of [overview, schedule]) {
    assert.match(source, /formatExactCurrency/)
    assert.doesNotMatch(source, /parseFloat|BigInt|BigNumber/)
    assert.doesNotMatch(source, /\.reduce\(/)
  }

  assert.match(overview, /Não representa baixas, pagamentos realizados nem saldo efetivamente em aberto/)
  assert.match(schedule, /Não indica se uma parcela foi paga, está em aberto ou em atraso/)
  assert.match(schedule, /type="month"/)
  assert.match(schedule, /<Pager/)
})
