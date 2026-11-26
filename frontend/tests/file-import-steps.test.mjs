import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  confirmBlockedReason,
  fileImportReducer,
  importSteps,
  initialFileImportState,
} from "../src/features/accounting-area/lib/file-import.ts"

// #528: importação do Razão em 2 etapas explícitas (Pré-visualizar → Confirmar e importar).

function read(path) {
  return readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")
}

const PREVIEW = {
  fileSha256: "a".repeat(64),
  format: "HTML_XLS",
  layoutProfile: "analytical-ledger-html-v1",
  periodStart: "2026-01-01",
  periodEnd: "2026-01-31",
  totalRows: 12,
  movementCount: 10,
  invalidRowCount: 0,
  observedAccountCount: 4,
  warnings: [],
  errors: [],
  sampleRows: [],
}

const file = new File(["synthetic"], "razao.xls")

function ready(preview) {
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file })
  state = fileImportReducer(state, { type: "preview-started", sourceId: "s1" })
  return fileImportReducer(state, { type: "preview-succeeded", preview })
}

function statuses(state) {
  return importSteps(state, "s1").map((step) => `${step.label}:${step.status}`)
}

test("the two steps are always named the same way", () => {
  assert.deepEqual(
    importSteps(initialFileImportState, null).map((step) => step.label),
    ["Pré-visualizar", "Confirmar e importar"],
  )
})

test("before a valid preview only step 1 is current; nothing is written yet", () => {
  assert.deepEqual(statuses(initialFileImportState), ["Pré-visualizar:current", "Confirmar e importar:pending"])
  const selected = fileImportReducer(initialFileImportState, { type: "select-file", file })
  assert.deepEqual(statuses(selected), ["Pré-visualizar:current", "Confirmar e importar:pending"])
})

test("a valid preview moves the user to step 2", () => {
  const state = ready(PREVIEW)
  assert.deepEqual(statuses(state), ["Pré-visualizar:done", "Confirmar e importar:current"])
  assert.equal(confirmBlockedReason(state), null)

  const confirming = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k", sourceId: "s1" })
  assert.deepEqual(statuses(confirming), ["Pré-visualizar:done", "Confirmar e importar:current"])

  const done = fileImportReducer(confirming, {
    type: "confirm-succeeded",
    receipt: { accountingSourceId: "s1", coveredFrom: "2026-01-01", coveredTo: "2026-01-31", rowCount: 12, publishedCount: 10, skippedCount: 2, committedAt: "2026-02-01T12:00:00Z" },
  })
  assert.deepEqual(statuses(done), ["Pré-visualizar:done", "Confirmar e importar:done"])
})

test("a preview with errors blocks step 2 and says why", () => {
  const state = ready({ ...PREVIEW, errors: [{ code: "E", sourceRow: 3, message: "x" }, { code: "E", sourceRow: 4, message: "y" }] })
  assert.deepEqual(statuses(state), ["Pré-visualizar:done", "Confirmar e importar:blocked"])
  assert.match(confirmBlockedReason(state), /2 erros/)
  assert.match(confirmBlockedReason(state), /nada foi gravado/i)

  const noPeriod = ready({ ...PREVIEW, periodStart: null, periodEnd: null })
  assert.match(confirmBlockedReason(noPeriod), /período/)
})

test("a preview made for another source does not unlock step 2", () => {
  const state = ready(PREVIEW)
  assert.deepEqual(
    importSteps(state, "other").map((step) => step.status),
    ["done", "blocked"],
  )
})

test("the screen makes the two steps explicit and highlights the final action", () => {
  const screen = read("features/accounting-area/pages/file-import-screen.tsx")
  assert.match(screen, /importSteps\(/)
  assert.match(screen, /Nada será gravado nesta etapa/)
  assert.match(screen, /Etapa 1 de 2/)
  assert.match(screen, /Etapa 2 de 2/)
  assert.match(screen, /"Confirmar e importar"/)
  assert.match(screen, /confirmBlockedReason\(/)
  // A empresa ativa aparece no passo final e no recibo.
  assert.ok((screen.match(/company\.legalName|company\?\.legalName/g) ?? []).length >= 3)
  assert.doesNotMatch(screen, /sincroniz/i)
  assert.doesNotMatch(screen, /fileSha256\}|idempotencyKey\}/)
  for (const match of screen.matchAll(/<button\b[\s\S]*?>/g)) {
    assert.match(match[0], /onClick=|type="submit"|disabled(?!=\{false\})/, match[0])
  }
})
