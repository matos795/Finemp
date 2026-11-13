import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { getMonthlyContractBillingPreview } from "../src/features/contracts/api/contracts-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("Faturamento do mês is published with contract permissions", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/contracts/pages/monthly-contract-billing-page.tsx", import.meta.url),
    "utf8",
  )

  const item = findNavigationByPath(
    navigationModules,
    "/clients/monthly-billing",
  ).item

  assert.equal(item.id, "fatMes")
  assert.deepEqual(item.permissions, ["CONTRACT_VIEW", "CONTRACT_MANAGE"])
  assert.match(
    router,
    /path="\/clients\/monthly-billing"[\s\S]*?<MonthlyContractBillingPage/,
  )
  assert.match(page, /getMonthlyContractBillingPreview/)
  assert.doesNotMatch(page, /incomeTotal\s*[+\-*/]|expenseTotal\s*[+\-*/]|parseFloat|Number\(/)
})

test("monthly billing API keeps company and competence explicit", async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      month: "2026-11",
      incomeTotal: "0.00",
      expenseTotal: "0.00",
      netTotal: "0.00",
      contracts: {
        items: [],
        page: 0,
        size: 50,
        totalItems: 0,
        totalPages: 0,
      },
    })
  }

  await getMonthlyContractBillingPreview(
    "company-1",
    "2026-11",
    0,
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/contracts/billing-preview?month=2026-11&page=0&size=50",
  )
})
