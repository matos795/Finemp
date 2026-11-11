import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { reverseFinancialEntrySettlement } from "../src/features/financial-entries/api/financial-entries-api.ts"
import {
  REVERSAL_DONE_MESSAGE,
  canReverseFinancialEntrySettlement,
  reversalConfirmation,
} from "../src/features/financial-entries/lib/financial-entry-settlement.ts"
import { describeFinancialEntryError } from "../src/features/financial-entries/lib/financial-entry-errors.ts"
import { refreshCompanyFinancialData } from "../src/features/financial-entries/lib/financial-entry-refresh.ts"
import { nextCommandAttempt } from "../src/features/financial-entries/lib/command-key.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const COMPANY = "c0000000-0000-4000-8000-000000000001"
const OTHER_COMPANY = "c0000000-0000-4000-8000-000000000002"
const KEY = "k0000000-0000-4000-8000-000000000001"

const can = (...permissions) => (permission) => permissions.includes(permission)
const entry = (status, description = "Aluguel fictício") => ({
  id: "e1",
  version: 6,
  status,
  values: { description, direction: "EXPENSE", paymentDate: status === "PAID" ? "2026-11-09" : null },
})

test("reversal posts exactly expectedVersion with CSRF and Idempotency-Key", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json({ companyId: COMPANY, commandId: "x", entryId: "e1", action: "SETTLEMENT_REVERSED", version: 7 })
  }

  const receipt = await reverseFinancialEntrySettlement(COMPANY, "e1", 6, KEY)

  const command = calls.find((call) => call.path !== "/api/auth/csrf")
  assert.equal(command.path, `/api/companies/${COMPANY}/financial-entries/e1/settlement-reversals`)
  assert.equal(command.options.method, "POST")
  assert.equal(new Headers(command.options.headers).get("Idempotency-Key"), KEY)
  assert.equal(new Headers(command.options.headers).get("X-XSRF-TOKEN"), "t")
  assert.equal(command.options.body, "{\"expectedVersion\":6}")
  assert.equal(receipt.action, "SETTLEMENT_REVERSED")
})

test("Desfazer baixa appears only for PAID with FINANCIAL_ENTRY_MANAGE (UX rule, not domain)", () => {
  const manage = can("FINANCIAL_ENTRY_MANAGE")
  assert.equal(canReverseFinancialEntrySettlement(entry("PAID"), manage), true)
  assert.equal(canReverseFinancialEntrySettlement(entry("OPEN"), manage), false)
  assert.equal(canReverseFinancialEntrySettlement(entry("OVERDUE"), manage), false)
  assert.equal(canReverseFinancialEntrySettlement(entry("PAID"), can("FINANCIAL_ENTRY_VIEW")), false)
  assert.equal(canReverseFinancialEntrySettlement(entry("PAID"), can("BANK_ACCOUNT_MANAGE", "FINANCIAL_CATEGORY_MANAGE")), false)
  assert.equal(canReverseFinancialEntrySettlement(entry("PAID"), () => false), false)
})

test("confirmation names the entry, says it returns to open and keeps the bank account", () => {
  const confirmation = reversalConfirmation(entry("PAID", "Venda fictícia 9"))
  assert.equal(confirmation.title, "Desfazer a baixa de \"Venda fictícia 9\"?")
  assert.equal(confirmation.message, "O título voltará para em aberto. A conta bancária usada na baixa será mantida no lançamento.")
  assert.equal(confirmation.confirmLabel, "Desfazer baixa")
  assert.equal(REVERSAL_DONE_MESSAGE, "Baixa desfeita: voltou para em aberto.")
})

test("reversal errors map to messages without internal codes", () => {
  const message = (status, code) => describeFinancialEntryError(new ApiError(status, "x", code), "reverse")

  const stale = message(409, "STALE_VERSION")
  assert.match(stale, /recarregue/i)
  assert.doesNotMatch(stale, /STALE_VERSION/)
  assert.match(message(409, "FINANCIAL_PERIOD_CLOSED"), /fechado.*não pode ser realizada/)
  assert.match(message(409, "IDEMPOTENCY_MISMATCH"), /recarregue/i)
  assert.match(message(400, "INVALID_FINANCIAL_ENTRY_COMMAND"), /Recarregue a lista e tente de novo/)
  assert.match(message(401), /sessão/)
  assert.match(message(403), /permissão para desfazer baixas/)
  assert.match(message(404), /não encontrado/)
  assert.match(message(409, "FINANCIAL_ENTRY_CONFLICT"), /Recarregue/)
  assert.match(message(429), /Aguarde/)
  assert.match(message(503), /indisponível/)
  assert.match(describeFinancialEntryError(new TypeError("offline"), "reverse"), /conexão/)
  for (const status of [400, 401, 403, 404, 409, 429, 503]) {
    assert.doesNotMatch(message(status, "SOME_CODE"), /SOME_CODE/)
  }
})

test("a resend of the same reversal reuses the key; another entry version gets a new one", () => {
  const body = (version) => JSON.stringify({ action: "reverse", entryId: "e1", expectedVersion: version })
  let n = 0
  const newKey = () => `key-${++n}`
  const first = nextCommandAttempt(null, body(6), newKey)
  assert.equal(nextCommandAttempt(first, body(6), newKey).key, first.key)
  assert.notEqual(nextCommandAttempt(first, body(7), newKey).key, first.key)
})

test("success refreshes entries and financial reports of the operation Company only", async () => {
  const invalidated = []
  const queryClient = { invalidateQueries: async (filters) => { invalidated.push(filters.queryKey) } }

  await refreshCompanyFinancialData(queryClient, COMPANY)

  assert.deepEqual(invalidated, [
    ["companies", COMPANY, "financial-entries"],
    ["companies", COMPANY, "financial-reports"],
  ])
  assert.equal(invalidated.flat().includes(OTHER_COMPANY), false)
})
