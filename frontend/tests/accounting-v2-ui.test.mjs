import { afterEach, test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  accountingV2Access,
  chartUniverse,
  defaultMonthRange,
  describeCoverage,
  movementMetadataView,
  NOT_SYNCHRONIZED_METADATA_MESSAGE,
  totalsPeriod,
} from "../src/features/accounting-area/lib/accounting-v2.ts"
import {
  getAccountingChart,
  getDatasetCoverage,
  getDebitCreditTotals,
  getOriginCatalog,
  getReadableAccountingSources,
  searchMovements,
} from "../src/features/accounting-area/api/accounting-data-api.ts"
import { formatExactDecimal } from "../src/features/accounting-area/lib/exact-decimal.ts"
import { accountingScreens, findAccountingScreenById } from "../src/features/accounting-area/lib/accounting-screens.ts"

// C2.9 (#475): telas contábeis ligadas às APIs v2 do #474/#509.

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function read(path) {
  return readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")
}

const NEW_SCREENS = [
  "features/accounting-area/pages/chart-screen.tsx",
  "features/accounting-area/pages/movements-screen.tsx",
  "features/accounting-area/pages/debit-credit-totals-screen.tsx",
  "features/accounting-area/components/coverage-status.tsx",
]

test("FULL_CHART is presented as the complete chart; IMPORTED_ACCOUNTS_ONLY never is", () => {
  const full = chartUniverse("FULL_CHART")
  assert.equal(full.complete, true)
  assert.equal(full.title, "Plano de Contas")

  const imported = chartUniverse("IMPORTED_ACCOUNTS_ONLY")
  assert.equal(imported.complete, false)
  assert.equal(imported.title, "Contas importadas/observadas")
  assert.doesNotMatch(imported.title + imported.note, /plano de contas completo(?! ainda não)/i)
  assert.match(imported.note, /não é o plano completo/i)
})

test("synchronized metadata shows every raw field and keeps empty source values distinguishable", () => {
  const view = movementMetadataView({
    sourceMetadataStatus: "SYNCHRONIZED",
    sourceMetadata: {
      sourceRecordedAt: "2026-03-10T14:15:16Z",
      historyCode: "H7",
      originCode: "OR3",
      entryTypeCode: "LN",
      dataOriginCode: "DO1",
      batchCode: "L42",
      documentNumber: null,
      operationGroupKey: "GRP-1",
      transactionKey: "TX-9",
      originEstablishmentCode: "2",
    },
  })

  assert.equal(view.synchronized, true)
  assert.deepEqual(
    view.fields.map((field) => field.key),
    [
      "sourceRecordedAt", "historyCode", "originCode", "entryTypeCode", "dataOriginCode",
      "batchCode", "documentNumber", "operationGroupKey", "transactionKey", "originEstablishmentCode",
    ],
  )
  assert.equal(view.fields.find((field) => field.key === "originCode").value, "OR3")
  assert.equal(view.fields.find((field) => field.key === "documentNumber").value, null)
  assert.equal(view.fields.find((field) => field.key === "documentNumber").empty, true)
})

test("missing metadata says so instead of pretending empty fields", () => {
  for (const item of [
    { sourceMetadataStatus: "NOT_SYNCHRONIZED", sourceMetadata: null },
    // Resposta anterior ao #509: sem os campos novos.
    {},
  ]) {
    const view = movementMetadataView(item)
    assert.equal(view.synchronized, false)
    assert.equal(view.message, NOT_SYNCHRONIZED_METADATA_MESSAGE)
  }

  assert.equal(NOT_SYNCHRONIZED_METADATA_MESSAGE, "Metadados detalhados ainda não importados para este lançamento.")
})

test("interval coverage keeps imported intervals and the gaps between them", () => {
  const view = describeCoverage({
    accountingSourceId: "s",
    dataset: "MOVEMENTS",
    kind: "INTERVALS",
    establishmentCode: null,
    available: true,
    importedFrom: "2026-01-01",
    importedTo: "2026-04-30",
    intervals: [
      { importedFrom: "2026-01-01", importedTo: "2026-01-31" },
      { importedFrom: "2026-03-01", importedTo: "2026-03-31" },
      { importedFrom: "2026-04-01", importedTo: "2026-04-30" },
    ],
    publishedAt: null,
  })

  assert.equal(view.state, "intervals")
  assert.equal(view.intervals.length, 3)
  assert.deepEqual(view.gaps, [{ from: "2026-02-01", to: "2026-02-28" }])
})

test("snapshot coverage shows the publication instant and no period", () => {
  const view = describeCoverage({
    accountingSourceId: "s",
    dataset: "CHART",
    kind: "SNAPSHOT",
    establishmentCode: null,
    available: true,
    importedFrom: null,
    importedTo: null,
    intervals: [],
    publishedAt: "2026-03-31T12:00:00Z",
  })

  assert.equal(view.state, "snapshot")
  assert.equal(view.publishedAt, "2026-03-31T12:00:00Z")
  assert.deepEqual(view.intervals, [])
  assert.deepEqual(view.gaps, [])

  assert.equal(
    describeCoverage({ kind: "SNAPSHOT", available: false, intervals: [], publishedAt: null }).state,
    "none",
  )
  assert.equal(describeCoverage({ kind: "INTERVALS", available: false, intervals: [] }).state, "none")
})

test("coverage wording never claims 'synchronized until'", () => {
  for (const path of NEW_SCREENS) {
    assert.doesNotMatch(read(path), /sincronizad[oa]s? até/i, path)
  }
  assert.match(read("features/accounting-area/components/coverage-status.tsx"), /Dados importados|Cobertura importada/)
})

test("totals period always covers whole months for both granularities", () => {
  assert.deepEqual(totalsPeriod("2026-02", "2026-03"), { startDate: "2026-02-01", endDate: "2026-03-31", valid: true })
  assert.deepEqual(totalsPeriod("2024-02", "2024-02"), { startDate: "2024-02-01", endDate: "2024-02-29", valid: true })
  assert.equal(totalsPeriod("2026-04", "2026-03").valid, false)
})

test("totals API sends granularity, period and optional filters exactly", async () => {
  const calls = []
  globalThis.fetch = async (path) => {
    calls.push(path)
    return Response.json({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 })
  }

  await getDebitCreditTotals("c1", {
    accountingSourceId: "s1",
    granularity: "MONTHLY",
    startDate: "2026-01-01",
    endDate: "2026-03-31",
    establishmentCode: " 2 ",
    sourceAccountId: "",
    page: 1,
    size: 50,
  })

  const url = new URL(calls[0], "http://x")
  assert.equal(url.pathname, "/api/companies/c1/accounting-debit-credit-totals")
  assert.equal(url.searchParams.get("granularity"), "MONTHLY")
  assert.equal(url.searchParams.get("startDate"), "2026-01-01")
  assert.equal(url.searchParams.get("endDate"), "2026-03-31")
  assert.equal(url.searchParams.get("establishmentCode"), "2")
  assert.equal(url.searchParams.has("sourceAccountId"), false)
  assert.equal(url.searchParams.get("page"), "1")
})

test("v2 read APIs hit the official endpoints with the source id", async () => {
  const calls = []
  globalThis.fetch = async (path) => {
    calls.push(path)
    return Response.json({})
  }

  await getAccountingChart("c1", { accountingSourceId: "s1", search: " caixa ", page: 0, size: 50 })
  await getDatasetCoverage("c1", { accountingSourceId: "s1", dataset: "CHART" })
  await getOriginCatalog("c1", "s1")
  await searchMovements("c1", { accountingSourceId: "s1", startDate: "2026-03-01", endDate: "2026-03-31", page: 0, size: 50 })

  assert.equal(calls[0], "/api/companies/c1/accounting-chart?accountingSourceId=s1&page=0&size=50&search=caixa")
  assert.equal(calls[1], "/api/companies/c1/accounting-dataset-coverage?accountingSourceId=s1&dataset=CHART")
  assert.equal(calls[2], "/api/companies/c1/accounting-origin-catalog?accountingSourceId=s1")
  assert.match(calls[3], /^\/api\/companies\/c1\/accounting-movements\/search\?/)
  assert.doesNotMatch(calls[3], /accountId=/)
})

test("money stays an exact decimal string end to end", () => {
  assert.equal(formatExactDecimal("99999999999.9999"), "99.999.999.999,9999")
  assert.equal(formatExactDecimal("0.0100"), "0,01")

  const totals = read("features/accounting-area/pages/debit-credit-totals-screen.tsx")
  assert.match(totals, /formatExactDecimal\(row\.debitTotal\)/)
  assert.match(totals, /formatExactDecimal\(row\.creditTotal\)/)
  assert.doesNotMatch(totals, /Number\(|parseFloat|parseInt|toFixed/)
})

test("debit/credit totals are never called a balance", () => {
  const totals = read("features/accounting-area/pages/debit-credit-totals-screen.tsx")
  for (const match of totals.matchAll(/saldo/gi)) {
    const before = totals.slice(Math.max(0, match.index - 12), match.index)
    assert.match(before, /não (é|são) $/i, `"saldo" só pode aparecer negado: …${before}saldo`)
  }
  assert.doesNotMatch(totals, /balance/i)
})

test("permissions decide which v2 entries are offered", () => {
  assert.deepEqual(accountingV2Access([]), { chart: false, ledger: false, coverage: false })
  assert.deepEqual(accountingV2Access(["ACCOUNT_CLASSIFICATION_VIEW"]), { chart: true, ledger: false, coverage: true })
  assert.deepEqual(accountingV2Access(["ACCOUNT_CLASSIFICATION_MANAGE"]), { chart: true, ledger: false, coverage: true })
  assert.deepEqual(accountingV2Access(["ACCOUNTING_MOVEMENT_VIEW"]), { chart: false, ledger: true, coverage: true })
  assert.deepEqual(accountingV2Access(["DRE_VIEW"]), { chart: false, ledger: false, coverage: false })

  // As telas novas conferem a permissão antes de consultar e mostram o estado "sem acesso".
  for (const path of NEW_SCREENS.slice(0, 3)) {
    const source = read(path)
    assert.match(source, /accountingV2Access\(/, path)
    assert.match(source, /Sem acesso/, path)
  }
})

test("source picker uses the dedicated read-only accounting source endpoint", async () => {
  const calls = []
  globalThis.fetch = async (path) => {
    calls.push(path)
    return Response.json([])
  }

  await getReadableAccountingSources("c1")

  assert.deepEqual(calls, ["/api/companies/c1/accounting-sources/readable"])
  const hook = read("features/accounting-area/hooks/use-accounting-sources.ts")
  assert.match(hook, /getReadableAccountingSources/)
  assert.doesNotMatch(hook, /useCompanySyncStatus|searchImportedAccounts|knownSourceIds/)
})

test("new screens are reachable inside Contabilidade without knowing old routes", () => {
  const chart = findAccountingScreenById("plano")
  const movements = findAccountingScreenById("ctbmov")
  const totals = findAccountingScreenById("ctbtot")

  assert.equal(chart.label, "Plano de Contas")
  assert.equal(chart.path, "/contabilidade/central-de-fechamento/qualidade/plano-de-contas")
  assert.equal(movements.label, "Movimentos Contábeis")
  assert.equal(movements.path, "/contabilidade/central-de-fechamento/base-contabil/movimentos")
  assert.equal(totals.label, "Totais de Débito e Crédito")
  assert.equal(totals.path, "/contabilidade/central-de-fechamento/base-contabil/totais-debito-credito")

  for (const screen of [chart, movements, totals]) {
    assert.equal(screen.prototype, false, screen.id)
    assert.equal(screen.status, "available", screen.id)
  }

  const layout = read("features/accounting-area/components/accounting-area-layout.tsx")
  assert.match(layout, /"plano"/)
  assert.match(layout, /"ctbmov"/)
  assert.match(layout, /"ctbtot"/)
  assert.equal(accountingScreens.filter((screen) => !screen.prototype).length, 3)
})

test("legacy routes stay published", () => {
  const router = read("app/router/app-router.tsx")
  assert.match(router, /path="\/accounting-accounts"/)
  assert.match(router, /path="\/accounting-movements"/)
  assert.match(router, /path="\/dre"/)
  assert.match(router, /path=\{`\$\{ACCOUNTING_AREA_BASE_PATH\}\/\*`\}/)
})

test("De-Para states which universe it is classifying", () => {
  const depara = read("features/accounting-area/pages/depara-screen.tsx")
  assert.match(depara, /ChartUniverseNotice/)
  assert.match(depara, /classifyImportedAccount/)
  assert.match(depara, /useInfiniteQuery/)
})

test("no primary action is a button without a handler", () => {
  for (const path of [...NEW_SCREENS, "features/accounting-area/pages/depara-screen.tsx"]) {
    const source = read(path)
    for (const match of source.matchAll(/<button\b[\s\S]*?>/g)) {
      assert.match(match[0], /onClick=|type="submit"|disabled(?!=\{false\})/, `${path}: ${match[0]}`)
    }
  }
})

test("period screens open on the last imported month, never on a sync run", () => {
  const coverage = {
    kind: "INTERVALS",
    available: true,
    importedFrom: "2026-01-01",
    importedTo: "2026-03-31",
    intervals: [{ importedFrom: "2026-01-01", importedTo: "2026-03-31" }],
  }

  assert.deepEqual(defaultMonthRange(coverage, "2026-11"), { from: "2026-03", to: "2026-03" })
  assert.deepEqual(defaultMonthRange({ kind: "INTERVALS", available: false, intervals: [] }, "2026-11"), { from: "2026-11", to: "2026-11" })
  assert.deepEqual(defaultMonthRange(undefined, "2026-11"), { from: "2026-11", to: "2026-11" })

  for (const path of ["features/accounting-area/pages/movements-screen.tsx", "features/accounting-area/pages/debit-credit-totals-screen.tsx"]) {
    const source = read(path)
    assert.match(source, /defaultMonthRange\(/, path)
    assert.doesNotMatch(source, /syncedUntil|latestSucceededRun/, path)
  }
})
