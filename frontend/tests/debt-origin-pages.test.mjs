import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createDebt,
  getDebtSchedule,
  listDebts,
  updateDebt,
} from "../src/features/debt/api/debts-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("four debt origins are real permission-guarded routes", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/debt/pages/debt-origin-page.tsx", import.meta.url),
    "utf8",
  )

  const expected = [
    ["/debts/banks", "divBanco", "BANK"],
    ["/debts/suppliers", "divForn", "SUPPLIER"],
    ["/debts/taxes", "divImp", "TAX"],
    ["/debts/third-parties", "divTerc", "THIRD_PARTY"],
  ]

  for (const [path, id, origin] of expected) {
    const item = findNavigationByPath(navigationModules, path).item
    assert.equal(item.id, id)
    assert.deepEqual(item.permissions, ["DEBT_VIEW", "DEBT_MANAGE"])
    assert.match(router, new RegExp(`path="${path}"[\\s\\S]*?origin="${origin}"`))
  }

  assert.match(page, /getDebtSchedule/)
  assert.match(page, /installment\.interestAmount/)
  assert.match(page, /installment\.amortizationAmount/)
  assert.match(page, /debt\.effectiveMonthlyRate/)

  assert.doesNotMatch(page, /parseFloat/)
  assert.doesNotMatch(
    page,
    /Number\(\s*(?:principalAmount|inputRate|fixedInstallmentAmount)/,
  )
})

test("debt queries keep origin, paging and schedule on the server", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })

    if (String(path).endsWith("/schedule")) {
      return Response.json([])
    }

    return Response.json({
      items: [],
      page: 2,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await listDebts(
    "company-1",
    "BANK",
    "  capital giro  ",
    2,
  )

  await getDebtSchedule(
    "company-1",
    "debt-1",
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/debts?origin=BANK&page=2&size=50&search=capital+giro",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/debts/debt-1/schedule",
  )
})


test("debt writes preserve exact decimal text and optimistic version", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })

    if (String(path) === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "test-token",
      })
    }

    return Response.json({
      id: "debt-1",
      companyId: "company-1",
      businessPartnerId: "partner-1",
      businessPartnerName: "Banco Sintético",
      origin: "BANK",
      description: "Capital de giro",
      amortizationSystem: "SAC",
      principalAmount: "1000.00",
      installmentCount: 3,
      inputRate: "3",
      inputRatePeriod: "MONTHLY",
      fixedInstallmentAmount: null,
      effectiveMonthlyRate: "3.000000000000",
      firstDueDate: "2026-01-31",
      version: 7,
      createdAt: "",
      updatedAt: "",
    })
  }

  const values = {
    businessPartnerId: "partner-1",
    origin: "BANK",
    description: "Capital de giro",
    amortizationSystem: "SAC",
    principalAmount: "1000.00",
    installmentCount: 3,
    inputRate: "3.000000000000",
    inputRatePeriod: "MONTHLY",
    fixedInstallmentAmount: null,
    firstDueDate: "2026-01-31",
  }

  await createDebt("company-1", values)
  await updateDebt(
    "company-1",
    { id: "debt-1", version: 7 },
    values,
  )

  const writes = calls.filter(
    (call) => String(call.path) !== "/api/auth/csrf",
  )

  const createBody = JSON.parse(writes[0].options.body)
  const updateBody = JSON.parse(writes[1].options.body)

  assert.equal(typeof createBody.principalAmount, "string")
  assert.equal(createBody.principalAmount, "1000.00")
  assert.equal(typeof createBody.inputRate, "string")
  assert.equal(createBody.inputRate, "3.000000000000")
  assert.equal(createBody.expectedVersion, null)

  assert.equal(typeof updateBody.principalAmount, "string")
  assert.equal(updateBody.expectedVersion, 7)
})
