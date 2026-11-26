import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  BALANCE_DEPENDENT_CHECKS,
  diagnosticsPeriod,
  accountsCheck,
  chartCheck,
  dreChecks,
  groupChecks,
  syncCheck,
} from "../src/features/accounting-area/lib/base-diagnostics.ts"
import { accountingScreens, findAccountingScreenById } from "../src/features/accounting-area/lib/accounting-screens.ts"

// #530: Diagnóstico das Bases parcial, só com verificações de contratos oficiais.

function read(path) {
  return readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")
}

const COVERED_JAN_MAR = [
  { accountingSourceId: "s1", importedFrom: "2026-01-01", importedTo: "2026-03-31", intervals: [{ importedFrom: "2026-01-01", importedTo: "2026-03-31" }] },
]
const GAP_FEB = [
  { accountingSourceId: "s1", importedFrom: "2026-01-01", importedTo: "2026-03-31", intervals: [{ importedFrom: "2026-01-01", importedTo: "2026-01-31" }, { importedFrom: "2026-03-01", importedTo: "2026-03-31" }] },
]

function byId(checks) {
  return Object.fromEntries(checks.map((check) => [check.id, check]))
}

test("DRE-based checks: full coverage, no unclassified accounts, computable DRE are approved", () => {
  const checks = byId(dreChecks({ state: "ok", dre: { unclassifiedAccountCount: 0, coverage: COVERED_JAN_MAR } }, "2026-01-01", "2026-03-31"))
  assert.equal(checks.coverage.status, "ok")
  assert.equal(checks.unclassified.status, "ok")
  assert.equal(checks.dreComputable.status, "ok")
})

test("coverage gaps and unclassified accounts require attention and keep the gap visible", () => {
  const checks = byId(dreChecks({ state: "ok", dre: { unclassifiedAccountCount: 3, coverage: GAP_FEB } }, "2026-01-01", "2026-03-31"))
  assert.equal(checks.coverage.status, "attention")
  assert.match(checks.coverage.detail, /01\/02\/2026 a 28\/02\/2026/)
  assert.equal(checks.unclassified.status, "attention")
  assert.match(checks.unclassified.detail, /3 contas/)
  assert.equal(checks.unclassified.resolveScreenId, "depara")

  const none = byId(dreChecks({ state: "ok", dre: { unclassifiedAccountCount: 0, coverage: [] } }, "2026-01-01", "2026-03-31"))
  assert.equal(none.coverage.status, "attention")
  assert.match(none.coverage.detail, /não importado/i)
})

test("a DRE refused for negative amounts is an inconsistency, and dependent checks are not concluded", () => {
  const checks = byId(dreChecks({ state: "negative-amount" }, "2026-01-01", "2026-03-31"))
  assert.equal(checks.dreComputable.status, "attention")
  assert.match(checks.dreComputable.detail, /valor negativo/)
  assert.equal(checks.coverage.status, "unavailable")
  assert.equal(checks.unclassified.status, "unavailable")
})

test("missing permission and failures never pretend a verification passed", () => {
  for (const check of dreChecks({ state: "forbidden" }, "2026-01-01", "2026-03-31")) assert.equal(check.status, "no-access")
  for (const check of dreChecks({ state: "error" }, "2026-01-01", "2026-03-31")) assert.equal(check.status, "unavailable")
  for (const check of dreChecks({ state: "loading" }, "2026-01-01", "2026-03-31")) assert.equal(check.status, "loading")
  assert.equal(accountsCheck({ state: "forbidden" }).status, "no-access")
  assert.equal(chartCheck({ state: "error" }).status, "unavailable")
  assert.equal(syncCheck({ state: "forbidden" }).status, "no-access")
})

test("pending imported accounts come from the official summary", () => {
  assert.equal(accountsCheck({ state: "ok", summary: { total: 10, classified: 10, pending: 0 } }).status, "ok")
  const pending = accountsCheck({ state: "ok", summary: { total: 10, classified: 7, pending: 3 } })
  assert.equal(pending.status, "attention")
  assert.match(pending.detail, /3 de 10/)
  assert.equal(pending.resolveScreenId, "depara")
})

test("the full chart is checked per source; a file import source only has observed accounts", () => {
  assert.equal(chartCheck({ state: "ok", sources: [{ type: "QUESTOR", availability: "FULL_CHART", publishedAt: "2026-03-31T12:00:00Z" }] }).status, "ok")
  const missing = chartCheck({ state: "ok", sources: [{ type: "QUESTOR", availability: "IMPORTED_ACCOUNTS_ONLY", publishedAt: null }] })
  assert.equal(missing.status, "attention")
  assert.equal(missing.resolveScreenId, "plano")
  const file = chartCheck({ state: "ok", sources: [{ type: "FILE_IMPORT", availability: "IMPORTED_ACCOUNTS_ONLY", publishedAt: null }] })
  assert.equal(file.status, "ok")
  assert.match(file.detail, /contas observadas/)
  assert.equal(chartCheck({ state: "ok", sources: [] }).status, "unavailable")
})

test("a refused Questor sync is reported with its code, without inventing a cause", () => {
  assert.equal(syncCheck({ state: "ok", items: [{ type: "QUESTOR", latestRun: { status: "SUCCEEDED", failureCode: null } }] }).status, "ok")
  const failed = syncCheck({ state: "ok", items: [{ type: "QUESTOR", latestRun: { status: "FAILED", failureCode: "INVALID_PAYLOAD", endDate: "2026-03-31" } }] })
  assert.equal(failed.status, "attention")
  assert.match(failed.detail, /INVALID_PAYLOAD/)
  assert.equal(syncCheck({ state: "ok", items: [] }).status, "ok")
})

test("checks are grouped as the prototype panels: inconsistencies and approved verifications", () => {
  const groups = groupChecks([
    { id: "a", status: "attention" },
    { id: "b", status: "ok" },
    { id: "c", status: "no-access" },
    { id: "d", status: "unavailable" },
  ])
  assert.deepEqual(groups.inconsistencies.map((check) => check.id), ["a"])
  assert.deepEqual(groups.approved.map((check) => check.id), ["b"])
  assert.deepEqual(groups.notVerified.map((check) => check.id), ["c", "d"])
})

test("balance-dependent prototype checks stay explicitly 'Em breve'", () => {
  assert.equal(BALANCE_DEPENDENT_CHECKS.length, 3)
  assert.match(BALANCE_DEPENDENT_CHECKS[0].label, /Ativo/)
  assert.match(BALANCE_DEPENDENT_CHECKS[1].label, /DRE/)
  const screen = read("features/accounting-area/pages/diag-screen.tsx")
  assert.match(screen, /BALANCE_DEPENDENT_CHECKS/)
  assert.match(screen, /Em breve/)
})

test("the screen is partial, uses only official contracts and never says 'synchronized until'", () => {
  const diag = findAccountingScreenById("diag")
  assert.equal(diag.status, "partial")
  assert.equal(diag.path, "/contabilidade/central-de-fechamento/qualidade/diagnostico")
  assert.equal(accountingScreens.filter((screen) => screen.status === "available" && screen.prototype).length, 0)

  const screen = read("features/accounting-area/pages/diag-screen.tsx")
  for (const call of ["getAccountingDre(", "searchImportedAccounts(", "getAccountingChart(", "getReadableAccountingSources("]) {
    assert.ok(screen.includes(call), call)
  }
  assert.match(screen, /useCompanySyncStatus\(/)
  assert.doesNotMatch(screen, /sincronizad[oa] até/i)
  assert.doesNotMatch(screen, /Gravidade/)
  for (const match of screen.matchAll(/<button\b[\s\S]*?>/g)) {
    assert.match(match[0], /onClick=|type="submit"|disabled(?!=\{false\})/, match[0])
  }

  const layout = read("features/accounting-area/components/accounting-area-layout.tsx")
  assert.match(layout, /"diag"/)
})

test("the default period follows the area competence until the user picks another one", () => {
  assert.deepEqual(diagnosticsPeriod(null, "2026-02"), { from: "2025-03", to: "2026-02" })
  assert.deepEqual(diagnosticsPeriod(null, "2026-11"), { from: "2025-12", to: "2026-11" })
  assert.deepEqual(diagnosticsPeriod({ from: "2026-01", to: "2026-03" }, "2026-11"), { from: "2026-01", to: "2026-03" })
  assert.match(read("features/accounting-area/pages/diag-screen.tsx"), /diagnosticsPeriod\(/)
})
