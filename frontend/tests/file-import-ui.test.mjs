import { afterEach, test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  FILE_IMPORT_FORMAT,
  FILE_IMPORT_LAYOUT_PROFILE,
  confirmRequest,
  fileImportErrorMessage,
  fileImportReducer,
  fileImportSources,
  historyRows,
  initialFileImportState,
  previewIsConfirmable,
  selectFileImportSource,
} from "../src/features/accounting-area/lib/file-import.ts"
import {
  confirmGeneralLedger,
  getFileImportHistory,
  prepareFileImportSource,
  previewGeneralLedger,
} from "../src/features/accounting-area/api/file-import-api.ts"
import { ApiError } from "../src/lib/api/api-client.ts"
import { accountingModules, findAccountingScreenById } from "../src/features/accounting-area/lib/accounting-screens.ts"

// F4 (#483): importar Razão por arquivo (FILE_IMPORT), sem Connector nem QUESTOR_SYNC.

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function read(path) {
  return readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")
}

function file(name, content = "synthetic") {
  return new File([content], name, { type: "application/vnd.ms-excel" })
}

const VALID_PREVIEW = {
  fileSha256: "a".repeat(64),
  format: "HTML_XLS",
  layoutProfile: "analytical-ledger-html-v1",
  periodStart: "2026-01-01",
  periodEnd: "2026-01-31",
  totalRows: 12,
  movementCount: 10,
  invalidRowCount: 0,
  observedAccountCount: 4,
  warnings: [{ code: "W1", sourceRow: 7, message: "Linha sem histórico." }],
  errors: [],
  sampleRows: [],
}

const RECEIPT = {
  accountingSourceId: "s1",
  idempotencyKey: "00000000-0000-4000-8000-000000000001",
  coveredFrom: "2026-01-01",
  coveredTo: "2026-01-31",
  rowCount: 12,
  publishedCount: 10,
  skippedCount: 2,
  committedAt: "2026-02-01T12:00:00Z",
}

// ------------------------------------------------------------------ sources

test("only active FILE_IMPORT sources are offered", () => {
  const sources = fileImportSources([
    { id: "q", type: "QUESTOR", active: true, connectionMode: "CONNECTOR", externalReference: null },
    { id: "f1", type: "FILE_IMPORT", active: true, connectionMode: "UPLOAD", externalReference: null },
    { id: "f2", type: "FILE_IMPORT", active: false, connectionMode: "UPLOAD", externalReference: null },
  ])
  assert.deepEqual(sources.map((source) => source.id), ["f1"])
  assert.deepEqual(fileImportSources(undefined), [])
})

test("one source is selected automatically; several require an explicit choice", () => {
  const one = [{ id: "f1" }]
  const many = [{ id: "f1" }, { id: "f2" }]

  assert.equal(selectFileImportSource(one, null), "f1")
  assert.equal(selectFileImportSource(many, null), null)
  assert.equal(selectFileImportSource(many, "f2"), "f2")
  // Escolha que não está mais na lista não vale.
  assert.equal(selectFileImportSource(many, "gone"), null)
  assert.equal(selectFileImportSource([], null), null)
})

// --------------------------------------------------------------------- flow

test("choosing another file invalidates the previous preview", () => {
  const first = file("razao-jan.xls")
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file: first })
  state = fileImportReducer(state, { type: "preview-started" })
  state = fileImportReducer(state, { type: "preview-succeeded", preview: VALID_PREVIEW })
  assert.equal(state.status, "preview-ready")
  assert.equal(previewIsConfirmable(state), true)

  state = fileImportReducer(state, { type: "select-file", file: file("razao-fev.xls") })
  assert.equal(state.status, "file-selected")
  assert.equal(state.preview, null)
  assert.equal(previewIsConfirmable(state), false)
})

test("a preview with errors blocks confirmation", () => {
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file: file("razao.xls") })
  state = fileImportReducer(state, { type: "preview-started" })
  state = fileImportReducer(state, {
    type: "preview-succeeded",
    preview: { ...VALID_PREVIEW, errors: [{ code: "E1", sourceRow: 3, message: "Data inválida." }] },
  })

  assert.equal(previewIsConfirmable(state), false)
  const blocked = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k1" })
  assert.equal(blocked, state)
})

test("confirmation re-sends the same File with the preview hash, profile and format", () => {
  const chosen = file("razao.xls")
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file: chosen })
  state = fileImportReducer(state, { type: "preview-started" })
  state = fileImportReducer(state, { type: "preview-succeeded", preview: VALID_PREVIEW })
  state = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k1" })

  const request = confirmRequest(state)
  assert.equal(request.file, chosen)
  assert.equal(request.expectedFileSha256, VALID_PREVIEW.fileSha256)
  assert.equal(request.idempotencyKey, "k1")
  assert.equal(request.layoutProfile, "analytical-ledger-html-v1")
  assert.equal(request.format, "HTML_XLS")
  assert.equal(FILE_IMPORT_LAYOUT_PROFILE, "analytical-ledger-html-v1")
  assert.equal(FILE_IMPORT_FORMAT, "HTML_XLS")
})

test("a second confirm while one is running is ignored (no double submit)", () => {
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file: file("razao.xls") })
  state = fileImportReducer(state, { type: "preview-started" })
  state = fileImportReducer(state, { type: "preview-succeeded", preview: VALID_PREVIEW })
  state = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k1" })
  const again = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k2" })

  assert.equal(again, state)
  assert.equal(confirmRequest(again).idempotencyKey, "k1")

  const screen = read("features/accounting-area/pages/file-import-screen.tsx")
  assert.match(screen, /confirmingRef/)
  assert.match(screen, /crypto\.randomUUID\(\)/)
})

test("after an error, an explicit retry gets a new idempotency key; success keeps the receipt", () => {
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file: file("razao.xls") })
  state = fileImportReducer(state, { type: "preview-started" })
  state = fileImportReducer(state, { type: "preview-succeeded", preview: VALID_PREVIEW })
  state = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k1" })
  state = fileImportReducer(state, { type: "confirm-failed", message: "Falhou." })
  assert.equal(state.status, "preview-ready")
  assert.equal(state.error, "Falhou.")
  assert.equal(previewIsConfirmable(state), true)

  state = fileImportReducer(state, { type: "confirm-started", idempotencyKey: "k2" })
  assert.equal(confirmRequest(state).idempotencyKey, "k2")

  state = fileImportReducer(state, { type: "confirm-succeeded", receipt: RECEIPT })
  assert.equal(state.status, "done")
  assert.equal(state.receipt, RECEIPT)
  assert.equal(previewIsConfirmable(state), false)
})

test("a stale preview response for a replaced file is dropped", () => {
  let state = fileImportReducer(initialFileImportState, { type: "select-file", file: file("a.xls") })
  state = fileImportReducer(state, { type: "preview-started" })
  const token = state.fileToken
  state = fileImportReducer(state, { type: "select-file", file: file("b.xls") })
  state = fileImportReducer(state, { type: "preview-succeeded", preview: VALID_PREVIEW, fileToken: token })

  assert.equal(state.status, "file-selected")
  assert.equal(state.preview, null)
})

// ---------------------------------------------------------------------- API

test("preview, confirm, prepare and history use the official endpoints with multipart", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "t" })
    if (String(path).endsWith("/history")) return Response.json([])
    if (String(path).endsWith("/source")) return Response.json({ id: "f1", type: "FILE_IMPORT", connectionMode: "UPLOAD", active: true })
    if (String(path).endsWith("/confirm")) return Response.json(RECEIPT)
    return Response.json(VALID_PREVIEW)
  }

  const chosen = file("razao.xls")
  await previewGeneralLedger("s1", chosen)
  await confirmGeneralLedger("s1", { file: chosen, expectedFileSha256: "a".repeat(64), idempotencyKey: "k1" })
  await prepareFileImportSource("c1")
  await getFileImportHistory("s1")

  const preview = calls.find((call) => String(call.path).endsWith("/general-ledger/preview"))
  assert.equal(preview.path, "/api/accounting-sources/s1/file-imports/general-ledger/preview")
  assert.equal(preview.options.method, "POST")
  assert.ok(preview.options.body instanceof FormData)
  assert.equal(preview.options.body.get("file"), chosen)
  assert.equal(preview.options.body.get("layoutProfile"), "analytical-ledger-html-v1")
  assert.equal(preview.options.body.get("format"), "HTML_XLS")
  // FormData: o navegador define o boundary; nunca forçar JSON.
  assert.equal(new Headers(preview.options.headers).get("Content-Type"), null)

  const confirm = calls.find((call) => String(call.path).endsWith("/general-ledger/confirm"))
  assert.equal(confirm.path, "/api/accounting-sources/s1/file-imports/general-ledger/confirm")
  assert.equal(confirm.options.body.get("file"), chosen)
  assert.equal(confirm.options.body.get("expectedFileSha256"), "a".repeat(64))
  assert.equal(confirm.options.body.get("idempotencyKey"), "k1")
  assert.equal(confirm.options.body.get("layoutProfile"), "analytical-ledger-html-v1")
  assert.equal(confirm.options.body.get("format"), "HTML_XLS")
  assert.equal(new Headers(confirm.options.headers).get("Content-Type"), null)

  const prepare = calls.find((call) => String(call.path).endsWith("/source"))
  assert.equal(prepare.path, "/api/companies/c1/accounting-file-import/source")
  assert.equal(prepare.options.method, "POST")

  assert.ok(calls.some((call) => call.path === "/api/accounting-sources/s1/file-imports/history"))
  // Nunca usa a gestão geral de fontes (QUESTOR_SYNC).
  assert.ok(!calls.some((call) => /\/accounting-sources$/.test(String(call.path))))
})

// ------------------------------------------------------------------- errors

test("errors are sanitized and never leak server details", () => {
  assert.match(fileImportErrorMessage(new ApiError(409, "x", "PREVIEW_FILE_MISMATCH")), /diferente do arquivo pré-visualizado/)
  assert.match(fileImportErrorMessage(new ApiError(409, "x", "PERIOD_OVERLAP")), /já importado/)
  assert.match(fileImportErrorMessage(new ApiError(400, "x", "INVALID_GENERAL_LEDGER")), /layout homologado/)
  assert.match(fileImportErrorMessage(new ApiError(400, "x", "INVALID_FILE")), /vazio, inválido ou excede/)
  assert.match(fileImportErrorMessage(new ApiError(403, "x")), /Sem permissão/)
  assert.match(fileImportErrorMessage(new ApiError(500, "java.lang.NullPointerException at com.finemp")), /Não foi possível/)
  assert.doesNotMatch(fileImportErrorMessage(new Error("SELECT * FROM x")), /SELECT/)
  for (const message of [
    fileImportErrorMessage(new ApiError(409, "x", "PREVIEW_FILE_MISMATCH")),
    fileImportErrorMessage(new ApiError(409, "x", "IDEMPOTENCY_CONFLICT")),
  ]) {
    assert.doesNotMatch(message, /sha|hash|fingerprint|idempot/i)
  }
})

// ------------------------------------------------------------------ history

test("history shows only period, counts and date, never technical identifiers", () => {
  const rows = historyRows([
    { ...RECEIPT, fileSha256: "b".repeat(64), fingerprint: "fp", committedAt: "2026-02-01T12:00:00Z" },
  ])
  assert.deepEqual(Object.keys(rows[0]).sort(), ["committedAt", "coveredFrom", "coveredTo", "publishedCount", "rowCount", "skippedCount"])

  const screen = read("features/accounting-area/pages/file-import-screen.tsx")
  assert.doesNotMatch(screen, /\{[^}]*(fileSha256|idempotencyKey|fingerprint)[^}]*\}\s*<\//)
  assert.doesNotMatch(screen, /receipt\.idempotencyKey|preview\.fileSha256\}/)
})

// ---------------------------------------------------------------- the screen

test("the screen uses 'imported', never 'synchronized', and never calls observed accounts a full chart", () => {
  const screen = read("features/accounting-area/pages/file-import-screen.tsx")
  assert.doesNotMatch(screen, /sincroniz/i)
  assert.match(screen, /importad/i)
  assert.match(screen, /não é o plano de contas completo/i)
  assert.doesNotMatch(screen, /contrapartida/i)
  assert.doesNotMatch(screen, /reportedBalance/)
  assert.doesNotMatch(screen, /QUESTOR_SYNC/)
  assert.match(screen, /ACCOUNTING_IMPORT_MANAGE/)
})

test("every state of the flow has a visible message", () => {
  const screen = read("features/accounting-area/pages/file-import-screen.tsx")
  for (const text of [
    "Carregando as fontes",
    "Preparar fonte por arquivo",
    "Preparando a fonte",
    "Escolha o arquivo do Razão",
    "Gerando a pré-visualização",
    "Pré-visualização válida",
    "A pré-visualização encontrou erros",
    "Confirmando a importação",
    "Importação concluída",
    "Sem acesso",
  ]) {
    assert.match(screen, new RegExp(text), text)
  }
})

test("after success it offers shortcuts to Movements and De-Para", () => {
  const screen = read("features/accounting-area/pages/file-import-screen.tsx")
  assert.match(screen, /findAccountingScreenById\("ctbmov"\)/)
  assert.match(screen, /findAccountingScreenById\("depara"\)/)
  for (const match of screen.matchAll(/<button\b[\s\S]*?>/g)) {
    assert.match(match[0], /onClick=|type="submit"|disabled(?!=\{false\})/, match[0])
  }
})

test("the screen lives in Central de Fechamento → Base contábil importada", () => {
  const screen = findAccountingScreenById("ctbimp")
  assert.equal(screen.label, "Importar Razão")
  assert.equal(screen.path, "/contabilidade/central-de-fechamento/base-contabil/importar-razao")
  assert.equal(screen.prototype, false)
  assert.equal(screen.status, "available")

  const base = accountingModules[0].subgroups.find((subgroup) => subgroup.label === "Base contábil importada")
  assert.deepEqual(base.screens.map((item) => item.id), ["ctbmov", "ctbtot", "ctbimp"])
  for (const subgroup of accountingModules[0].subgroups.filter((item) => item.label !== "Base contábil importada")) {
    assert.ok(!subgroup.screens.some((item) => item.id === "ctbimp"), subgroup.label)
  }

  const layout = read("features/accounting-area/components/accounting-area-layout.tsx")
  assert.match(layout, /"ctbimp"/)
})
