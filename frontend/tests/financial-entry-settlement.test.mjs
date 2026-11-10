import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { settleFinancialEntry } from "../src/features/financial-entries/api/financial-entries-api.ts"
import {
  canSettleFinancialEntry,
  newSettlementForm,
  settlementAccountOptions,
  settlementCopy,
  settlementDiffersFromNominal,
  toSettlementValues,
  validateSettlementForm,
} from "../src/features/financial-entries/lib/financial-entry-settlement.ts"
import { describeFinancialEntryError } from "../src/features/financial-entries/lib/financial-entry-errors.ts"
import { nextCommandAttempt } from "../src/features/financial-entries/lib/command-key.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const COMPANY = "c0000000-0000-4000-8000-000000000001"
const BANK = "b0000000-0000-4000-8000-000000000001"
const OTHER_BANK = "b0000000-0000-4000-8000-000000000002"
const OLD_BANK = "b0000000-0000-4000-8000-000000000003"
const KEY = "k0000000-0000-4000-8000-000000000001"
const TODAY = "2026-11-10"

function entry(overrides = {}) {
  return {
    id: "e1",
    version: 5,
    status: "OPEN",
    effectiveAmount: "1500.00",
    labels: { bankName: "Banco Fictício" },
    ...overrides,
    values: {
      direction: "EXPENSE",
      description: "Aluguel fictício",
      nominalAmount: "1500.00",
      dueDate: "2026-11-05",
      paymentDate: null,
      bankAccountId: BANK,
      ...overrides.values,
    },
  }
}

const can = (...permissions) => (permission) => permissions.includes(permission)

test("settlement posts exact text with Idempotency-Key and expectedVersion only", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json({ companyId: COMPANY, commandId: "x", entryId: "e1", action: "SETTLED", version: 6 })
  }

  const values = toSettlementValues({ paymentDate: "2026-11-09", amount: "1.510,00", bankAccountId: BANK })
  const receipt = await settleFinancialEntry(COMPANY, "e1", values, 5, KEY)

  const command = calls.find((call) => call.path !== "/api/auth/csrf")
  assert.equal(command.path, `/api/companies/${COMPANY}/financial-entries/e1/settlements`)
  assert.equal(command.options.method, "POST")
  assert.equal(new Headers(command.options.headers).get("Idempotency-Key"), KEY)
  assert.equal(new Headers(command.options.headers).get("X-XSRF-TOKEN"), "t")
  const body = JSON.parse(command.options.body)
  assert.deepEqual(body, {
    expectedVersion: 5,
    values: { paymentDate: "2026-11-09", paidAmount: "1510.00", bankAccountId: BANK },
  })
  assert.match(command.options.body, /"paidAmount":"1510\.00"/)
  assert.equal(receipt.action, "SETTLED")
})

test("Pagar/Receber appear only for OPEN or OVERDUE with FINANCIAL_ENTRY_MANAGE", () => {
  const manage = can("FINANCIAL_ENTRY_MANAGE")
  assert.equal(canSettleFinancialEntry(entry({ status: "OPEN" }), manage), true)
  assert.equal(canSettleFinancialEntry(entry({ status: "OVERDUE" }), manage), true)
  assert.equal(canSettleFinancialEntry(entry({ status: "PAID", values: { paymentDate: "2026-11-01" } }), manage), false)
  assert.equal(canSettleFinancialEntry(entry(), can("FINANCIAL_ENTRY_VIEW")), false)
  assert.equal(canSettleFinancialEntry(entry(), can("BANK_ACCOUNT_MANAGE", "FINANCIAL_CATEGORY_MANAGE")), false)

  assert.equal(settlementCopy("INCOME").action, "Receber")
  assert.equal(settlementCopy("INCOME").title, "Registrar recebimento")
  assert.equal(settlementCopy("EXPENSE").action, "Pagar")
  assert.equal(settlementCopy("EXPENSE").title, "Registrar pagamento")
})

test("dialog starts with today, the nominal amount and the entry account", () => {
  assert.deepEqual(newSettlementForm(entry(), TODAY), {
    paymentDate: TODAY,
    amount: "1500,00",
    bankAccountId: BANK,
  })
  // Retenções não entram no valor inicial: continua o nominal.
  assert.equal(newSettlementForm(entry({ values: { nominalAmount: "99.9" } }), TODAY).amount, "99,90")
})

test("UX validation requires a past or present date, a positive exact amount and an account", () => {
  const valid = { paymentDate: TODAY, amount: "1.500,00", bankAccountId: BANK }
  assert.deepEqual(validateSettlementForm(valid, TODAY), {})
  assert.deepEqual(validateSettlementForm({ ...valid, paymentDate: "2026-11-09" }, TODAY), {})

  assert.ok(validateSettlementForm({ ...valid, paymentDate: "2026-11-11" }, TODAY).paymentDate)
  assert.ok(validateSettlementForm({ ...valid, paymentDate: "" }, TODAY).paymentDate)
  for (const amount of ["", "0", "0,00", "-1", "1,234", "abc"]) {
    assert.ok(validateSettlementForm({ ...valid, amount }, TODAY).amount, amount)
  }
  assert.ok(validateSettlementForm({ ...valid, bankAccountId: "" }, TODAY).bankAccountId)
})

test("a different paid amount is flagged as full settlement with adjustment, never as partial", () => {
  const nominal = entry()
  assert.equal(settlementDiffersFromNominal({ amount: "1.500,00" }, nominal), false)
  assert.equal(settlementDiffersFromNominal({ amount: "1500" }, nominal), false)
  assert.equal(settlementDiffersFromNominal({ amount: "1500,0" }, nominal), false)
  assert.equal(settlementDiffersFromNominal({ amount: "1.400,00" }, nominal), true)
  assert.equal(settlementDiffersFromNominal({ amount: "1500,01" }, nominal), true)
  // Valor ainda inválido não mostra o aviso (a validação já orienta).
  assert.equal(settlementDiffersFromNominal({ amount: "abc" }, nominal), false)
})

test("accounts: active ones plus the current inactive one, flagged; no other inactive", () => {
  const accounts = [
    { id: BANK, name: "Banco Fictício", active: true },
    { id: OTHER_BANK, name: "Caixa Fictício", active: true },
    { id: "b-x", name: "Antiga Fictícia", active: false },
    { id: OLD_BANK, name: "Encerrada Fictícia", active: false },
  ]

  assert.deepEqual(
    settlementAccountOptions(entry(), accounts),
    [
      { id: BANK, name: "Banco Fictício", inactive: false },
      { id: OTHER_BANK, name: "Caixa Fictício", inactive: false },
    ],
  )

  assert.deepEqual(
    settlementAccountOptions(entry({ values: { bankAccountId: OLD_BANK } }), accounts),
    [
      { id: BANK, name: "Banco Fictício", inactive: false },
      { id: OTHER_BANK, name: "Caixa Fictício", inactive: false },
      { id: OLD_BANK, name: "Encerrada Fictícia", inactive: true },
    ],
  )
})

test("without the account catalog, only the entry's current account is offered", () => {
  assert.deepEqual(
    settlementAccountOptions(entry(), null),
    [{ id: BANK, name: "Banco Fictício", inactive: false }],
  )
})

test("settlement errors map to user messages without internal codes", () => {
  const stale = describeFinancialEntryError(new ApiError(409, "x", "STALE_VERSION"), "settle")
  assert.match(stale, /recarregue/i)
  assert.doesNotMatch(stale, /STALE_VERSION/)

  const closed = describeFinancialEntryError(new ApiError(409, "x", "FINANCIAL_PERIOD_CLOSED"), "settle")
  assert.match(closed, /fechado/)
  assert.match(closed, /não pode ser realizada/)

  assert.match(describeFinancialEntryError(new ApiError(400, "x"), "settle"), /Confira a data, o valor e a conta/)
  assert.match(describeFinancialEntryError(new ApiError(401, "x"), "settle"), /sessão/)
  assert.match(describeFinancialEntryError(new ApiError(403, "x"), "settle"), /permissão/)
  assert.match(describeFinancialEntryError(new ApiError(404, "x"), "settle"), /não encontrado/)
  assert.match(describeFinancialEntryError(new ApiError(409, "x", "FINANCIAL_ENTRY_CONFLICT"), "settle"), /Recarregue/)
  assert.match(describeFinancialEntryError(new ApiError(429, "x"), "settle"), /Aguarde/)
  assert.match(describeFinancialEntryError(new ApiError(503, "x"), "settle"), /indisponível/)
  assert.match(describeFinancialEntryError(new TypeError("offline"), "settle"), /conexão/)
})

test("a resend of the same settlement reuses the key; a changed settlement gets a new one", () => {
  const body = (values) => JSON.stringify({ entryId: "e1", expectedVersion: 5, values })
  let n = 0
  const newKey = () => `key-${++n}`
  const first = nextCommandAttempt(null, body({ paidAmount: "1500.00" }), newKey)
  assert.equal(nextCommandAttempt(first, body({ paidAmount: "1500.00" }), newKey).key, first.key)
  assert.notEqual(nextCommandAttempt(first, body({ paidAmount: "1400.00" }), newKey).key, first.key)
})
