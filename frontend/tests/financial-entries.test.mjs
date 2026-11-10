import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import {
  createFinancialEntry,
  financialEntryQuery,
  listFinancialEntries,
  updateFinancialEntry,
} from "../src/features/financial-entries/api/financial-entries-api.ts"
import {
  financialEntryFormFrom,
  newFinancialEntryForm,
  parseNominalAmount,
  toCommandValues,
  validateFinancialEntryForm,
  withDocumentDate,
} from "../src/features/financial-entries/lib/financial-entry-form.ts"
import { describeFinancialEntryError } from "../src/features/financial-entries/lib/financial-entry-errors.ts"
import { canManageFinancialEntries } from "../src/features/financial-entries/lib/financial-entry-access.ts"
import { nextCommandAttempt } from "../src/features/financial-entries/lib/command-key.ts"
import { emptyFinancialEntryFilter } from "../src/features/financial-entries/types/financial-entry.types.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const COMPANY = "c0000000-0000-4000-8000-000000000001"
const BANK = "b0000000-0000-4000-8000-000000000001"
const CATEGORY = "a0000000-0000-4000-8000-000000000001"
const KEY = "k0000000-0000-4000-8000-000000000001"

function filledForm(direction) {
  return {
    ...newFinancialEntryForm(direction, "2026-11-10", BANK),
    description: "Venda fictícia 001",
    amount: "1.234,56",
    categoryId: CATEGORY,
  }
}

/** Intercepta CSRF e o comando; devolve as chamadas feitas. */
function captureCommand(receipt) {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json(receipt, { status: options.method === "POST" ? 201 : 200 })
  }
  return calls
}

test("list sends only filled filters and keeps paging on the server", async () => {
  const calls = []
  globalThis.fetch = async (path) => {
    calls.push(path)
    return Response.json({ items: [], page: 2, size: 50, totalItems: 0, totalPages: 0 })
  }

  await listFinancialEntries(COMPANY, {
    ...emptyFinancialEntryFilter,
    from: "2026-11-01",
    to: "2026-11-30",
    direction: "EXPENSE",
    status: "OVERDUE",
    bankId: BANK,
    categoryId: CATEGORY,
    search: "  aluguel ",
  }, 2)

  const url = new URL(calls[0], "https://example.invalid")
  assert.equal(url.pathname, `/api/companies/${COMPANY}/financial-entries`)
  assert.deepEqual(Object.fromEntries(url.searchParams), {
    page: "2",
    size: "50",
    from: "2026-11-01",
    to: "2026-11-30",
    direction: "EXPENSE",
    status: "OVERDUE",
    bankId: BANK,
    categoryId: CATEGORY,
    search: "aluguel",
  })
})

test("empty filters send no query restriction and 'sem centro' never combines with centerId", () => {
  assert.deepEqual(
    Object.fromEntries(financialEntryQuery(emptyFinancialEntryFilter, 0)),
    { page: "0", size: "50" },
  )

  const withoutCenter = financialEntryQuery({ ...emptyFinancialEntryFilter, center: "_none" }, 0)
  assert.equal(withoutCenter.get("withoutCenter"), "true")
  assert.equal(withoutCenter.has("centerId"), false)

  const center = financialEntryQuery({ ...emptyFinancialEntryFilter, center: "r1" }, 0)
  assert.equal(center.get("centerId"), "r1")
  assert.equal(center.has("withoutCenter"), false)
})

test("create INCOME posts exact money text, Idempotency-Key and no expectedVersion", async () => {
  const calls = captureCommand({ companyId: COMPANY, commandId: "x", entryId: "e1", action: "CREATED", version: 0 })
  const values = toCommandValues(filledForm("INCOME"))

  const receipt = await createFinancialEntry(COMPANY, values, KEY)

  const command = calls.find((call) => call.path.endsWith("/financial-entries"))
  assert.equal(command.options.method, "POST")
  assert.equal(new Headers(command.options.headers).get("Idempotency-Key"), KEY)

  const body = JSON.parse(command.options.body)
  assert.deepEqual(Object.keys(body), ["values"])
  assert.equal(body.values.direction, "INCOME")
  assert.equal(body.values.nominalAmount, "1234.56")
  assert.equal(typeof body.values.nominalAmount, "string")
  assert.match(command.options.body, /"nominalAmount":"1234\.56"/)
  assert.equal(receipt.entryId, "e1")
})

test("create EXPENSE keeps optional references null and sends no paid amount", () => {
  const values = toCommandValues(filledForm("EXPENSE"))

  assert.equal(values.direction, "EXPENSE")
  assert.equal(values.paymentDate, null)
  assert.equal(values.partnerId, null)
  assert.equal(values.resultCenterId, null)
  assert.equal(values.costBehaviorOverride, null)
  assert.equal(values.paymentMethod, null)
  assert.equal(Object.hasOwn(values, "actualPaidAmount"), false)
  assert.equal(values.competence, "2026-11")
})

test("create marked as already paid sends the payment date", () => {
  const values = toCommandValues({ ...filledForm("EXPENSE"), paid: true, paymentDate: "2026-11-09" })
  assert.equal(values.paymentDate, "2026-11-09")
})

test("update sends the full values with expectedVersion", async () => {
  const calls = captureCommand({ companyId: COMPANY, commandId: "x", entryId: "e1", action: "UPDATED", version: 4 })
  const values = toCommandValues(filledForm("EXPENSE"))

  await updateFinancialEntry(COMPANY, "e1", values, 3, KEY)

  const command = calls.find((call) => call.path.endsWith("/financial-entries/e1"))
  assert.equal(command.options.method, "PUT")
  assert.equal(new Headers(command.options.headers).get("Idempotency-Key"), KEY)
  const body = JSON.parse(command.options.body)
  assert.equal(body.expectedVersion, 3)
  assert.deepEqual(Object.keys(body.values).sort(), [
    "bankAccountId", "categoryId", "competence", "costBehaviorOverride", "description",
    "direction", "documentDate", "documentNumber", "dueDate", "entryDate", "nominalAmount",
    "notes", "partnerId", "paymentDate", "paymentMethod", "resultCenterId",
  ])
})

test("editing preserves the payment read from the backend", () => {
  const paid = {
    id: "e1",
    version: 7,
    values: {
      direction: "EXPENSE", description: "Aluguel fictício", nominalAmount: "1500.00",
      documentDate: "2026-10-01", entryDate: "2026-10-01", competence: "2026-09",
      dueDate: "2026-10-05", paymentDate: "2026-10-06", actualPaidAmount: "1510.00",
      bankAccountId: BANK, categoryId: CATEGORY, partnerId: null, resultCenterId: null,
      costBehaviorOverride: "FIXED", paymentMethod: "Pix", documentNumber: "", notes: "",
    },
  }

  const form = financialEntryFormFrom(paid)
  assert.equal(form.amount, "1500,00")
  assert.equal(form.competenceEdited, true)

  const values = toCommandValues({ ...form, description: "Aluguel fictício outubro", paid: false, paymentDate: "" }, paid)
  assert.equal(values.paymentDate, "2026-10-06")
  assert.equal(values.direction, "EXPENSE")
  assert.equal(values.nominalAmount, "1500.00")
  assert.equal(values.costBehaviorOverride, "FIXED")
  assert.equal(values.documentNumber, null)
  assert.equal(Object.hasOwn(values, "actualPaidAmount"), false)
})

test("amount parsing is exact text, never a JSON number or rounding", () => {
  assert.deepEqual(parseNominalAmount("R$ 1.234,56"), { ok: true, value: "1234.56" })
  assert.deepEqual(parseNominalAmount("99999999999999999.99"), { ok: true, value: "99999999999999999.99" })
  assert.deepEqual(parseNominalAmount("0010,5"), { ok: true, value: "10.5" })
  assert.deepEqual(parseNominalAmount("1.000"), { ok: true, value: "1000" })
  for (const invalid of ["", "0", "0,00", "-10", "1,234", "abc", "1e3", "123456789012345678"]) {
    assert.equal(parseNominalAmount(invalid).ok, false, invalid)
  }
})

test("document date drives competence until the user edits it", () => {
  const form = newFinancialEntryForm("INCOME", "2026-11-10")
  assert.equal(withDocumentDate(form, "2026-12-03").competence, "2026-12")
  assert.equal(withDocumentDate({ ...form, competence: "2026-10", competenceEdited: true }, "2026-12-03").competence, "2026-10")
})

test("form validation is UX only: required fields and formats", () => {
  const errors = validateFinancialEntryForm(
    { ...newFinancialEntryForm("EXPENSE", "2026-11-10"), competence: "2026-13" },
    { requireResultCenter: true, editing: false },
  )
  assert.deepEqual(Object.keys(errors).sort(), ["amount", "bankAccountId", "categoryId", "competence", "description", "resultCenterId"])
  assert.deepEqual(validateFinancialEntryForm(filledForm("INCOME"), { requireResultCenter: false, editing: false }), {})
  assert.equal(validateFinancialEntryForm(filledForm("INCOME"), { requireResultCenter: true, editing: true }).resultCenterId, undefined)
})

test("write controls require FINANCIAL_ENTRY_MANAGE, never a role", () => {
  assert.equal(canManageFinancialEntries((permission) => permission === "FINANCIAL_ENTRY_VIEW"), false)
  assert.equal(canManageFinancialEntries(() => false), false)
  assert.equal(canManageFinancialEntries((permission) => permission === "FINANCIAL_ENTRY_MANAGE"), true)
})

test("Idempotency-Key is reused only for the same body", () => {
  let n = 0
  const newKey = () => `key-${++n}`
  const first = nextCommandAttempt(null, "{\"a\":1}", newKey)
  assert.equal(first.key, "key-1")
  assert.equal(nextCommandAttempt(first, "{\"a\":1}", newKey).key, "key-1")
  assert.equal(nextCommandAttempt(first, "{\"a\":2}", newKey).key, "key-2")
  assert.match(nextCommandAttempt(null, "{}").key, /^[0-9a-f-]{36}$/)
})

test("main API errors map to user messages without internal codes", () => {
  const stale = describeFinancialEntryError(new ApiError(409, "x", "STALE_VERSION"), "update")
  assert.match(stale, /recarregue/i)
  assert.doesNotMatch(stale, /STALE_VERSION/)

  const closed = describeFinancialEntryError(new ApiError(409, "x", "FINANCIAL_PERIOD_CLOSED"), "create")
  assert.match(closed, /fechado/)
  assert.match(closed, /não pode ser realizada/)

  assert.match(describeFinancialEntryError(new ApiError(400, "x", "INVALID_FINANCIAL_ENTRY_COMMAND"), "create"), /Confira os campos/)
  assert.match(describeFinancialEntryError(new ApiError(403, "x"), "list"), /não tem acesso/)
  assert.match(describeFinancialEntryError(new ApiError(403, "x"), "update"), /permissão/)
  assert.match(describeFinancialEntryError(new ApiError(404, "x"), "update"), /não encontrado/)
  assert.match(describeFinancialEntryError(new ApiError(404, "x"), "list"), /não estão disponíveis/)
  assert.match(describeFinancialEntryError(new ApiError(401, "x"), "create"), /sessão/)
  assert.match(describeFinancialEntryError(new TypeError("offline"), "create"), /conexão/)
})

test("catalog selectors load every server page with role filtered on the server", async () => {
  const { listActiveBusinessPartners } = await import("../src/features/financial-entries/api/financial-catalogs-api.ts")
  const calls = []
  globalThis.fetch = async (path) => {
    calls.push(path)
    const page = Number(new URL(path, "https://example.invalid").searchParams.get("page"))
    return Response.json({ items: [{ id: `p${page}` }], page, size: 100, totalItems: 2, totalPages: 2 })
  }

  const partners = await listActiveBusinessPartners(COMPANY, "SUPPLIER")

  assert.deepEqual(partners.map((partner) => partner.id), ["p0", "p1"])
  const first = new URL(calls[0], "https://example.invalid")
  assert.equal(first.pathname, `/api/companies/${COMPANY}/business-partners`)
  assert.equal(first.searchParams.get("role"), "SUPPLIER")
  assert.equal(first.searchParams.get("active"), "true")
  assert.equal(first.searchParams.get("size"), "100")
})
