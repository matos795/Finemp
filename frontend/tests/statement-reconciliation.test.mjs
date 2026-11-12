import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import {
  createEntryFromStatement,
  reconcileStatementItem,
} from "../src/features/statements/api/statements-api.ts"
import {
  statementCreateValues,
  statementEntryInitialForm,
} from "../src/features/statements/lib/statement-entry-form.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const COMPANY = "c0000000-0000-4000-8000-000000000001"
const STATEMENT = "s0000000-0000-4000-8000-000000000001"
const ENTRY = "e0000000-0000-4000-8000-000000000001"
const BANK = "b0000000-0000-4000-8000-000000000001"
const CATEGORY = "a0000000-0000-4000-8000-000000000001"
const KEY = "k0000000-0000-4000-8000-000000000001"

function statement(overrides = {}) {
  return {
    id: STATEMENT,
    bankAccountId: BANK,
    status: "PENDING",
    version: 3,
    values: {
      postedDate: "2026-11-12",
      signedAmount: "-12.34",
      memo: "Café em São Paulo",
      externalId: "fit-1",
      documentReference: "DOC-1",
      ...overrides.values,
    },
    ...overrides,
  }
}

test("statement form derives immutable bank facts and preserves accents", () => {
  const form = statementEntryInitialForm(statement())

  assert.equal(form.direction, "EXPENSE")
  assert.equal(form.amount, "12,34")
  assert.equal(form.bankAccountId, BANK)
  assert.equal(form.paymentDate, "2026-11-12")
  assert.equal(form.paid, true)
  assert.equal(form.description, "Café em São Paulo")
  assert.equal(form.documentDate, "2026-11-12")
  assert.equal(form.entryDate, "2026-11-12")
  assert.equal(form.dueDate, "2026-11-12")
  assert.equal(form.competence, "2026-11")
  assert.equal(form.documentNumber, "DOC-1")
})

test("create-from-statement payload excludes amount direction bank and payment date", () => {
  const values = statementCreateValues({
    direction: "EXPENSE",
    description: "Café em São Paulo",
    nominalAmount: "999999.99",
    documentDate: "2026-11-12",
    entryDate: "2026-11-12",
    competence: "2026-11",
    dueDate: "2026-11-12",
    paymentDate: "2099-01-01",
    bankAccountId: "tampered-bank",
    categoryId: CATEGORY,
    partnerId: null,
    resultCenterId: null,
    costBehaviorOverride: null,
    paymentMethod: "Pix",
    documentNumber: "DOC-1",
    notes: null,
  })

  assert.deepEqual(values, {
    description: "Café em São Paulo",
    documentDate: "2026-11-12",
    entryDate: "2026-11-12",
    competence: "2026-11",
    dueDate: "2026-11-12",
    categoryId: CATEGORY,
    partnerId: null,
    resultCenterId: null,
    costBehaviorOverride: null,
    paymentMethod: "Pix",
    documentNumber: "DOC-1",
    notes: null,
  })
  assert.equal("nominalAmount" in values, false)
  assert.equal("direction" in values, false)
  assert.equal("bankAccountId" in values, false)
  assert.equal("paymentDate" in values, false)
})

test("reconcile posts only ids and optimistic versions", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json({ companyId: COMPANY, itemId: STATEMENT, entryId: ENTRY, commandId: KEY, itemVersion: 4, entryVersion: 1, auditEventId: "a" })
  }

  await reconcileStatementItem(COMPANY, STATEMENT, 3, ENTRY, 0, KEY)
  const command = calls.find((call) => call.path !== "/api/auth/csrf")

  assert.equal(command.path, `/api/companies/${COMPANY}/statement-items/${STATEMENT}/reconcile`)
  assert.equal(new Headers(command.options.headers).get("Idempotency-Key"), KEY)
  assert.deepEqual(JSON.parse(command.options.body), {
    expectedStatementVersion: 3,
    entryId: ENTRY,
    expectedEntryVersion: 0,
  })
})

test("create-entry endpoint receives classification fields but never reconstructed bank facts", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json({ companyId: COMPANY, itemId: STATEMENT, entryId: ENTRY, commandId: KEY, itemVersion: 4, entryVersion: 1, auditEventId: "a" })
  }

  const values = {
    description: "Café em São Paulo",
    documentDate: "2026-11-12",
    entryDate: "2026-11-12",
    competence: "2026-11",
    dueDate: "2026-11-12",
    categoryId: CATEGORY,
    partnerId: null,
    resultCenterId: null,
    costBehaviorOverride: null,
    paymentMethod: "Pix",
    documentNumber: "DOC-1",
    notes: null,
  }

  await createEntryFromStatement(COMPANY, STATEMENT, 3, values, KEY)
  const command = calls.find((call) => call.path !== "/api/auth/csrf")
  const body = JSON.parse(command.options.body)

  assert.equal(command.path, `/api/companies/${COMPANY}/statement-items/${STATEMENT}/create-entry`)
  assert.equal(new Headers(command.options.headers).get("Idempotency-Key"), KEY)
  assert.deepEqual(body, { expectedStatementVersion: 3, values })
  assert.equal("nominalAmount" in body.values, false)
  assert.equal("bankAccountId" in body.values, false)
})
