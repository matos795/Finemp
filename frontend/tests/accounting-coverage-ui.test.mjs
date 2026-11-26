import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  COVERAGE_STATUS_TEXT,
  DRE_NOT_IMPORTED_TEXT,
  monthCoverage,
  periodCoverage,
} from "../src/features/accounting-area/lib/coverage-periods.ts"
import { inferredAccountKind } from "../src/features/accounting-area/lib/inferred-account-kind.ts"
import { entryCompositionLines, originLabel } from "../src/features/accounting-area/lib/entry-composition.ts"
import { findAccountingScreenById, accountingModules } from "../src/features/accounting-area/lib/accounting-screens.ts"

// C2.9 (#475), segunda fatia: De-Para consciente de CHART, Composição com metadata v2,
// DRE e Pré-fechamento conscientes da cobertura acumulada (#509/#510).

function read(path) {
  return readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")
}

// Cobertura MOVEMENTS como a DRE devolve (uma entrada por fonte).
const JAN_AND_MAR = [
  {
    accountingSourceId: "s1",
    importedFrom: "2026-01-01",
    importedTo: "2026-03-31",
    intervals: [
      { importedFrom: "2026-01-01", importedTo: "2026-01-31" },
      { importedFrom: "2026-03-01", importedTo: "2026-03-31" },
    ],
  },
]

test("a month inside coverage is covered; outside is not imported; across a gap is partial", () => {
  assert.equal(monthCoverage(JAN_AND_MAR, "2026-01"), "COVERED")
  assert.equal(monthCoverage(JAN_AND_MAR, "2026-02"), "NOT_IMPORTED")
  assert.equal(monthCoverage(JAN_AND_MAR, "2026-04"), "NOT_IMPORTED")
  assert.equal(monthCoverage([], "2026-01"), "NOT_IMPORTED")
  assert.equal(monthCoverage(undefined, "2026-01"), "NOT_IMPORTED")

  const midMonth = [{ accountingSourceId: "s1", importedFrom: "2026-01-10", importedTo: "2026-01-31", intervals: [{ importedFrom: "2026-01-10", importedTo: "2026-01-31" }] }]
  assert.equal(monthCoverage(midMonth, "2026-01"), "PARTIAL")
})

test("with several sources the month is covered only when every source covers it", () => {
  const twoSources = [
    JAN_AND_MAR[0],
    { accountingSourceId: "s2", importedFrom: null, importedTo: null, intervals: [] },
  ]

  assert.equal(monthCoverage(twoSources, "2026-01"), "PARTIAL")
  assert.equal(monthCoverage(twoSources, "2026-02"), "NOT_IMPORTED")
})

test("period coverage keeps the gaps inside the analysed period", () => {
  const view = periodCoverage(JAN_AND_MAR, "2026-01-01", "2026-03-31")
  assert.equal(view.status, "PARTIAL")
  assert.deepEqual(view.gaps, [{ from: "2026-02-01", to: "2026-02-28" }])
  assert.equal(view.text, "Cobertura parcial — existem lacunas no histórico importado")

  const full = periodCoverage(JAN_AND_MAR, "2026-03-01", "2026-03-31")
  assert.equal(full.status, "COVERED")
  assert.deepEqual(full.gaps, [])
  assert.equal(full.text, "Dados importados para todo o período")

  const none = periodCoverage(JAN_AND_MAR, "2026-05-01", "2026-06-30")
  assert.equal(none.status, "NOT_IMPORTED")
  assert.deepEqual(none.gaps, [{ from: "2026-05-01", to: "2026-06-30" }])
  assert.equal(none.text, "Histórico ainda não importado para este período")

  assert.deepEqual(COVERAGE_STATUS_TEXT, {
    COVERED: "Dados importados para todo o período",
    PARTIAL: "Cobertura parcial — existem lacunas no histórico importado",
    NOT_IMPORTED: "Histórico ainda não importado para este período",
  })
  assert.equal(DRE_NOT_IMPORTED_TEXT, "Histórico contábil ainda não importado para este período.")
})

test("synthetic/analytic is only the documented inference and always labelled as inferred", () => {
  assert.deepEqual(inferredAccountKind("1"), { label: "Sintética", tag: "Inferido" })
  assert.deepEqual(inferredAccountKind("2"), { label: "Analítica", tag: "Inferido" })
  assert.equal(inferredAccountKind("A"), null)
  assert.equal(inferredAccountKind(null), null)
  assert.equal(inferredAccountKind(" 2 ")?.label, "Analítica")

  const chart = read("features/accounting-area/pages/chart-screen.tsx")
  assert.match(chart, /inferredAccountKind\(/)
  assert.match(chart, /Inferido/)
})

test("origin catalog only complements the raw origin code", () => {
  const catalog = new Map([["FI", "INTEGRACAO FISCAL"]])
  assert.equal(originLabel("FI", catalog), "FI — INTEGRACAO FISCAL")
  assert.equal(originLabel("ZZ", catalog), "ZZ")
  assert.equal(originLabel("FI", undefined), "FI")
  assert.equal(originLabel(null, catalog), null)

  const detail = read("features/accounting-area/components/movement-metadata-detail.tsx")
  assert.match(detail, /originLabel\(/)
})

test("entry composition keeps only the lines of that entry in that source, in API order", () => {
  const items = [
    { id: "1", accountingSourceId: "s1", sourceEntryKey: "L1", side: "CREDIT" },
    { id: "2", accountingSourceId: "s1", sourceEntryKey: "L2", side: "CREDIT" },
    { id: "3", accountingSourceId: "s2", sourceEntryKey: "L2", side: "DEBIT" },
    { id: "4", accountingSourceId: "s1", sourceEntryKey: "L2", side: "DEBIT" },
  ]

  assert.deepEqual(
    entryCompositionLines(items, { accountingSourceId: "s1", sourceEntryKey: "L2" }).map((item) => item.id),
    ["2", "4"],
  )
})

test("movements open the entry composition with a real handler and reuse the modal pattern", () => {
  const movements = read("features/accounting-area/pages/movements-screen.tsx")
  assert.match(movements, /EntryCompositionModal/)
  assert.match(movements, /Composição/)

  const modal = read("features/accounting-area/components/entry-composition-modal.tsx")
  assert.match(modal, /className="modal"/)
  assert.match(modal, /role="dialog"/)
  assert.match(modal, /MovementMetadataDetail/)
  assert.match(modal, /searchMovements\(/)
  assert.match(modal, /Escape/)
})

test("De-Para states the universe with the agreed wording and keeps a single classification flow", () => {
  const notice = read("features/accounting-area/components/chart-universe-notice.tsx")
  assert.match(notice, /O plano completo ainda não foi publicado\. Esta classificação considera somente contas observadas nos dados importados\./)

  const depara = read("features/accounting-area/pages/depara-screen.tsx")
  assert.match(depara, /classifyImportedAccount/)
  assert.equal(depara.match(/classifyImportedAccount\(/g).length, 1)
  assert.match(depara, /useChartMembership/)
  assert.match(depara, /No plano completo/)
  assert.match(depara, /Só observada/)
  assert.match(depara, /inferredAccountKind\(/)
  assert.match(depara, /MovementDrilldownModal/)
  assert.match(depara, /"pend"/)
  assert.match(depara, /"todas"/)
  assert.match(depara, /fetchNextPage/)
})

test("pre-closing reads the accumulated coverage, never a sync run", () => {
  const cfpre = read("features/accounting-area/pages/cfpre-screen.tsx")
  assert.match(cfpre, /periodCoverage\(/)
  assert.match(cfpre, /monthCoverage\(/)
  assert.match(cfpre, /DRE_NOT_IMPORTED_TEXT/)
  assert.match(cfpre, /unclassifiedAccountCount/)
  assert.match(cfpre, /FULL_CHART/)
  assert.doesNotMatch(cfpre, /syncedUntil|latestSucceededRun|monthAfterImportedCoverage/)
  // Balancete e alertas sem backend continuam Em breve.
  assert.match(cfpre, /SoonPanel title="Alertas e recomendações"/)
  assert.match(cfpre, /SoonPanel title="Balancete de pré-fechamento"/)
})

test("the legacy DRE page tells 'no movement' from 'not imported'", () => {
  const dre = read("features/dre/pages/dre-page.tsx")
  assert.match(dre, /periodCoverage\(/)
  assert.match(dre, /DRE_NOT_IMPORTED_TEXT/)
  const types = read("features/dre/types/dre.types.ts")
  assert.match(types, /coverage/)
})

test("new and changed screens never say 'synchronized until'", () => {
  for (const path of [
    "features/accounting-area/pages/cfpre-screen.tsx",
    "features/accounting-area/pages/depara-screen.tsx",
    "features/accounting-area/components/entry-composition-modal.tsx",
    "features/accounting-area/components/chart-universe-notice.tsx",
    "features/accounting-area/lib/coverage-periods.ts",
    "features/dre/pages/dre-page.tsx",
  ]) {
    assert.doesNotMatch(read(path), /sincronizad[oa]s? até/i, path)
  }
})

test("no primary action is a button without a handler", () => {
  for (const path of [
    "features/accounting-area/pages/cfpre-screen.tsx",
    "features/accounting-area/pages/depara-screen.tsx",
    "features/accounting-area/pages/movements-screen.tsx",
    "features/accounting-area/pages/chart-screen.tsx",
    "features/accounting-area/components/entry-composition-modal.tsx",
    "features/accounting-area/components/movement-drilldown-modal.tsx",
  ]) {
    for (const match of read(path).matchAll(/<button\b[\s\S]*?>/g)) {
      assert.match(match[0], /onClick=|type="submit"|disabled(?!=\{false\})/, `${path}: ${match[0]}`)
    }
  }
})

test("navigation keeps the accounting flow together and legacy routes alive", () => {
  const quality = accountingModules[0].subgroups.find((subgroup) => subgroup.label === "Qualidade da base")
  const ids = quality.screens.map((screen) => screen.id)
  assert.equal(ids.indexOf("depara"), ids.indexOf("plano") + 1)

  const base = accountingModules[0].subgroups.find((subgroup) => subgroup.label === "Base contábil importada")
  assert.deepEqual(base.screens.map((screen) => screen.id), ["ctbmov", "ctbtot"])
  assert.ok(findAccountingScreenById("cfpre"))

  const router = read("app/router/app-router.tsx")
  for (const route of ["/accounting-accounts", "/accounting-movements", "/dre"]) {
    assert.match(router, new RegExp(`path="${route}"`))
  }
})

test("permissions keep gating each read", () => {
  const cfpre = read("features/accounting-area/pages/cfpre-screen.tsx")
  assert.match(cfpre, /DRE_VIEW/)
  assert.match(cfpre, /accountingV2Access\(/)
  const modal = read("features/accounting-area/components/entry-composition-modal.tsx")
  assert.match(modal, /Sem acesso/)
})
