import { test } from "node:test"
import assert from "node:assert/strict"
import {
  newSettlementForm,
  settlementAccountOptions,
  settlementCommand,
  settlementCopy,
  settlementDiffersFromNominal,
  settlementTargetFromEntry,
} from "../src/features/financial-entries/lib/financial-entry-settlement.ts"
import { refreshCompanyFinancialData } from "../src/features/financial-entries/lib/financial-entry-refresh.ts"
import {
  canSettleOpenTitle,
  settlementTargetFromOpenTitle,
} from "../src/features/reports/lib/open-title-settlement.ts"

const BANK = "b0000000-0000-4000-8000-000000000001"
const OTHER_BANK = "b0000000-0000-4000-8000-000000000002"
const COMPANY = "c0000000-0000-4000-8000-000000000001"
const OTHER_COMPANY = "c0000000-0000-4000-8000-000000000002"
const TODAY = "2026-11-10"

function openTitle(overrides = {}) {
  return {
    id: "t1",
    description: "Mensalidade fictícia",
    direction: "INCOME",
    nominalAmount: "2350.40",
    competence: "2026-10",
    dueDate: "2026-10-31",
    paymentDate: null,
    daysLate: 10,
    documentNumber: "NF-0001",
    documentDate: "2026-10-01",
    categoryId: "cat-1",
    band: "OVERDUE_1_30",
    bankAccountId: BANK,
    partnerId: "p1",
    resultCenterId: null,
    version: 7,
    ...overrides,
  }
}

const can = (...permissions) => (permission) => permissions.includes(permission)

test("open title becomes a settlement target with only fields the report already has", () => {
  assert.deepEqual(settlementTargetFromOpenTitle(openTitle()), {
    id: "t1",
    version: 7,
    direction: "INCOME",
    description: "Mensalidade fictícia",
    nominalAmount: "2350.40",
    dueDate: "2026-10-31",
    bankAccountId: BANK,
  })
})

test("financial entry becomes the same target shape, keeping the bank label", () => {
  const entry = {
    id: "e1",
    version: 3,
    status: "OPEN",
    labels: { bankName: "Banco Fictício" },
    values: {
      direction: "EXPENSE",
      description: "Aluguel fictício",
      nominalAmount: "1500.00",
      dueDate: "2026-11-05",
      paymentDate: null,
      bankAccountId: BANK,
    },
  }

  assert.deepEqual(settlementTargetFromEntry(entry), {
    id: "e1",
    version: 3,
    direction: "EXPENSE",
    description: "Aluguel fictício",
    nominalAmount: "1500.00",
    dueDate: "2026-11-05",
    bankAccountId: BANK,
    bankLabel: "Banco Fictício",
  })
})

test("report settlement needs FINANCIAL_ENTRY_MANAGE and an unpaid title; VIEW stays read-only", () => {
  const manage = can("FINANCIAL_ENTRY_MANAGE")
  assert.equal(canSettleOpenTitle(openTitle(), manage), true)
  assert.equal(canSettleOpenTitle(openTitle({ paymentDate: "2026-11-01" }), manage), false)
  assert.equal(canSettleOpenTitle(openTitle(), can("FINANCIAL_ENTRY_VIEW")), false)
  assert.equal(canSettleOpenTitle(openTitle(), can("CASH_FLOW_VIEW", "BANK_ACCOUNT_MANAGE")), false)
  assert.equal(canSettleOpenTitle(openTitle(), can()), false)
})

test("Receber for receivables and Pagar for payables, from the title direction", () => {
  const income = settlementTargetFromOpenTitle(openTitle({ direction: "INCOME" }))
  const expense = settlementTargetFromOpenTitle(openTitle({ direction: "EXPENSE" }))
  assert.equal(settlementCopy(income.direction).action, "Receber")
  assert.equal(settlementCopy(expense.direction).action, "Pagar")
})

test("dialog starts from the title nominal amount and account, with no extra entry fetch", () => {
  const target = settlementTargetFromOpenTitle(openTitle())
  assert.deepEqual(newSettlementForm(target, TODAY), {
    paymentDate: TODAY,
    amount: "2350,40",
    bankAccountId: BANK,
  })
  assert.equal(settlementDiffersFromNominal({ amount: "2.350,40" }, target), false)
  assert.equal(settlementDiffersFromNominal({ amount: "2.300,00" }, target), true)
})

test("without the account catalog the report offers only the title account, unnamed", () => {
  const target = settlementTargetFromOpenTitle(openTitle())
  assert.deepEqual(settlementAccountOptions(target, null), [
    { id: BANK, name: "Conta atual", inactive: false },
  ])
  assert.deepEqual(
    settlementAccountOptions(target, [
      { id: BANK, name: "Banco Fictício", active: true },
      { id: OTHER_BANK, name: "Caixa Fictício", active: true },
    ]).map((option) => option.id),
    [BANK, OTHER_BANK],
  )
})

test("settlement command sends the title id and version untouched, with exact values", () => {
  const target = settlementTargetFromOpenTitle(openTitle())
  const values = { paymentDate: "2026-11-09", paidAmount: "2350.40", bankAccountId: BANK }

  assert.deepEqual(settlementCommand(target, values), {
    entryId: "t1",
    expectedVersion: 7,
    values: { paymentDate: "2026-11-09", paidAmount: "2350.40", bankAccountId: BANK },
  })
})

test("after settlement only the same Company's entries and reports are refreshed", async () => {
  const invalidated = []
  const queryClient = { invalidateQueries: async ({ queryKey }) => { invalidated.push(queryKey) } }

  await refreshCompanyFinancialData(queryClient, COMPANY)

  assert.ok(invalidated.length > 0)
  assert.ok(invalidated.some((key) => key.includes("financial-reports")))
  for (const key of invalidated) {
    assert.equal(key[1], COMPANY)
    assert.ok(!key.includes(OTHER_COMPANY))
  }
})
