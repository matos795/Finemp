import { test } from "node:test"
import assert from "node:assert/strict"
import { readdirSync, readFileSync, statSync } from "node:fs"
import { join } from "node:path"
import {
  ACCOUNTING_AREA_BASE_PATH,
  ACCOUNTING_AREA_HOME,
  accountingModules,
  accountingScreens,
  findAccountingScreen,
  findAccountingScreenById,
} from "../src/features/accounting-area/lib/accounting-screens.ts"
import {
  canEnterAccountingArea,
  homePathFor,
  resolveSelection,
} from "../src/features/accounting-area/lib/accounting-access.ts"
import {
  defaultCompetence,
  monthLabel,
  nextMonth,
  syncedUntil,
} from "../src/features/accounting-area/lib/accounting-period.ts"
import { baseStatusText } from "../src/features/accounting-area/lib/base-status.ts"
import { menuMatches } from "../src/features/accounting-area/lib/menu-search.ts"
import { scopeCss, scopeSelector } from "../scripts/scope-accounting-css.mjs"

// Menu "Contabilidade (equipe Finemp)" do protótipo (navegacao/menu-contabil.json), na mesma ordem.
const PROTOTYPE_MENU = {
  "Central de Fechamento": {
    "Rotina do mês": [
      ["cfcock", "Cockpit de Fechamento"],
      ["cfrot", "Roteiro do Analista"],
      ["checklist", "Checklist de Fechamento"],
      ["cfdocs", "Documentos e Classificação"],
      ["cfbase", "Base de Classificação"],
      ["cfrec", "Lançamentos Recorrentes e Planilhas"],
      ["cfantecip", "Despesas Antecipadas"],
      ["cfadiant", "Adiantamentos (Fornecedores e Clientes)"],
      ["cfpre", "Pré-fechamento (Balancete e DRE)"],
      ["cflanc", "Lotes e Exportação Questor"],
    ],
    Fiscal: [
      ["cflalur", "IRPJ e CSLL · Lalur (Lucro Real)"],
      ["cfpres", "Validador de Lucro Presumido"],
      ["cfimp", "Validador de ISS, PIS e COFINS"],
    ],
    Folha: [["cffolha", "Validador e Conciliador da Folha"]],
    "Qualidade da base": [
      ["cfcong", "Períodos Congelados"],
      ["diag", "Diagnóstico das Bases"],
      ["depara", "De-Para do Plano de Contas"],
      ["cfvol", "Volumetria do Contrato"],
    ],
  },
  "Pacote de Consolidação": {
    "Demonstrações e movimentações": [
      ["gcmapa", "Estrutura e Mapa"],
      ["gcbs", "Balanço (BS)"],
      ["gcpl", "Resultado (PL)"],
      ["gcexps", "Custos por Natureza (EXPS)"],
      ["gcss", "Patrimônio Líquido (SS)"],
      ["gctfa", "Imobilizado (TFA)"],
      ["gpifa", "Intangível (IFA)"],
      ["gcbb", "Empréstimos (BB)"],
      ["gccf", "Fluxo de Caixa (CF)"],
      ["gpcfinfo", "Informações do Fluxo de Caixa (CF_INFO)"],
    ],
    "Informações complementares": [
      ["gpinvest", "Investimentos (INVEST)"],
      ["gplease", "Arrendamentos (LEASE)"],
      ["gppldtl", "Detalhe de Outros Resultados (PLDTL)"],
      ["gpmgt", "Perdas de Rentabilidade (MGT)"],
      ["gpexcng", "Câmbio (EXCNG)"],
      ["gpbonus", "Bônus (BONUS)"],
      ["gpinfo", "Informações da Empresa (INFO)"],
    ],
    Intercompany: [["gpint", "Intercompany (INT_BS / INT_PL)"]],
    "Projeção e orçamento": [
      ["gpfcst", "Projeção (PL_F)"],
      ["gpbud", "Orçamento (PL_B / EXPS_B)"],
    ],
  },
  "Sucesso do Cliente": {
    "": [["sucesso", "Acompanhamento das Ações"]],
  },
}

test("the accounting menu keeps the prototype groups, subgroups, codes and names in order", () => {
  const actual = Object.fromEntries(
    accountingModules.map((module) => [
      module.label,
      Object.fromEntries(
        module.subgroups.map((subgroup) => [subgroup.label, subgroup.screens.map((screen) => [screen.id, screen.label])]),
      ),
    ]),
  )

  assert.deepEqual(actual, PROTOTYPE_MENU)
  assert.equal(accountingScreens.length, 39)
})

test("every accounting screen has a unique route under the accounting area", () => {
  const paths = accountingScreens.map((screen) => screen.path)

  assert.equal(new Set(paths).size, paths.length)

  for (const screen of accountingScreens) {
    assert.ok(screen.path.startsWith(`${ACCOUNTING_AREA_BASE_PATH}/`), screen.id)
    assert.deepEqual(findAccountingScreen(screen.path)?.screen, screen)
    assert.equal(findAccountingScreenById(screen.id), screen)
  }

  assert.equal(findAccountingScreen(ACCOUNTING_AREA_HOME).screen.id, "cfcock")
  assert.equal(findAccountingScreen("/dashboard"), null)
})

test("screens mirror the prototype header block and competence bar", () => {
  const withBar = accountingScreens.filter((screen) => screen.competenceBar).map((screen) => screen.id)
  const withoutBlock = accountingScreens.filter((screen) => screen.tag === null).map((screen) => screen.id)

  assert.deepEqual(withBar, [
    "cfcock", "cfrot", "cfdocs", "cfbase", "cfrec", "cfantecip", "cfadiant", "cfpre", "cflanc",
    "cflalur", "cfpres", "cfimp", "cffolha", "cfvol",
  ])
  assert.deepEqual(withoutBlock, ["checklist", "diag", "depara"])
})

test("no screen is marked available without an official contract", () => {
  for (const screen of accountingScreens) {
    assert.notEqual(screen.status, "available", screen.id)
    assert.ok(screen.panels.length > 0, screen.id)
  }

  // Pré-fechamento (#446) e De-Para (#442/#444) usam contrato em parte; o restante continua "Em breve".
  assert.deepEqual(
    accountingScreens.filter((screen) => screen.status === "partial").map((screen) => screen.id),
    ["cfpre", "depara"],
  )
})

const membership = (overrides = {}) => ({
  accountingFirmId: "00000000-0000-0000-0000-0000000000f1",
  name: "Contabilidade demonstração",
  active: true,
  permissions: [],
  ...overrides,
})

test("only members of an active accounting firm enter the accounting area", () => {
  assert.equal(canEnterAccountingArea(undefined), false)
  assert.equal(canEnterAccountingArea({ platformPermissions: [], accountingFirms: [] }), false)
  assert.equal(canEnterAccountingArea({ platformPermissions: [], accountingFirms: [membership({ active: false })] }), false)
  assert.equal(canEnterAccountingArea({ platformPermissions: [], accountingFirms: [membership()] }), true)
})

test("users with companies keep landing on the company dashboard", () => {
  const member = { platformPermissions: [], accountingFirms: [membership()] }

  assert.equal(homePathFor(2, member, ACCOUNTING_AREA_HOME), "/dashboard")
  assert.equal(homePathFor(0, undefined, ACCOUNTING_AREA_HOME), "/dashboard")
  assert.equal(homePathFor(0, member, ACCOUNTING_AREA_HOME), ACCOUNTING_AREA_HOME)
})

const portfolio = {
  accountingFirms: [
    {
      accountingFirmId: "f1",
      name: "Contabilidade A",
      active: true,
      scope: "GRANTED_COMPANIES",
      clients: [
        { clientId: "c1", name: "Cliente 1", active: true, companies: [{ companyId: "e1", legalName: "Empresa 1" }] },
        {
          clientId: "c2",
          name: "Cliente 2",
          active: true,
          companies: [
            { companyId: "e2", legalName: "Empresa 2" },
            { companyId: "e3", legalName: "Empresa 3" },
          ],
        },
      ],
    },
    { accountingFirmId: "f2", name: "Contabilidade B", active: true, scope: "FULL_HIERARCHY", clients: [] },
  ],
}

test("the selection is resolved only inside the session portfolio", () => {
  assert.deepEqual(
    resolveSelection(portfolio, {}).company,
    { companyId: "e1", legalName: "Empresa 1" },
    "defaults to the first option of each level",
  )
  assert.equal(resolveSelection(portfolio, { firmId: "f1", clientId: "c2", companyId: "e3" }).company.companyId, "e3")

  // Escolha guardada que saiu da carteira (grant revogado) não é usada.
  const revoked = resolveSelection(portfolio, { firmId: "f1", clientId: "c2", companyId: "gone" })
  assert.equal(revoked.company.companyId, "e2")
  assert.equal(resolveSelection(portfolio, { firmId: "unknown" }).firm.accountingFirmId, "f1")

  const emptyFirm = resolveSelection(portfolio, { firmId: "f2" })
  assert.equal(emptyFirm.client, null)
  assert.equal(emptyFirm.company, null)
  assert.deepEqual(resolveSelection({ accountingFirms: [] }, {}), { firm: null, client: null, company: null })
})

test("the accounting area never navigates through administrative hierarchy listings", () => {
  const root = new URL("../src/features/accounting-area/", import.meta.url).pathname
  const files = []
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name)
      statSync(path).isDirectory() ? walk(path) : files.push(path)
    }
  }
  walk(root)

  for (const file of files) {
    const source = readFileSync(file, "utf8")
    assert.doesNotMatch(source, /platform-admin/, file)
    assert.doesNotMatch(source, /\/api\/accounting-firms\//, file)
  }
})

test("base coverage comes only from succeeded sync runs", () => {
  const run = (endDate, status = "SUCCEEDED") => ({ id: endDate, status, endDate })
  const status = {
    items: [
      { accountingSourceId: "s1", latestSucceededRun: run("2026-06-30"), latestRun: run("2026-07-31", "FAILED") },
      { accountingSourceId: "s2", latestSucceededRun: run("2026-09-30"), latestRun: null },
      { accountingSourceId: "s3", latestSucceededRun: null, latestRun: null },
    ],
  }

  assert.equal(syncedUntil(status), "2026-09-30")
  assert.equal(syncedUntil({ items: [] }), null)
  assert.equal(monthLabel("2026-09-30"), "set/26")
  assert.equal(nextMonth("2026-12-31"), "2027-01")
  assert.equal(defaultCompetence("2026-09-30", new Date(2026, 10, 24)), "2026-10")
  assert.equal(defaultCompetence(null, new Date(2026, 10, 24)), "2026-11")
})

test("the competence bar states missing base or access instead of inventing one", () => {
  const ok = { items: [{ latestSucceededRun: { endDate: "2026-09-30" } }] }

  assert.equal(baseStatusText({ hasCompany: true, loading: false, errorStatus: null, status: ok }), "Base contábil até set/26")
  assert.equal(
    baseStatusText({ hasCompany: true, loading: false, errorStatus: null, status: { items: [] } }),
    "Base contábil: nenhuma sincronização concluída",
  )
  assert.equal(
    baseStatusText({ hasCompany: true, loading: false, errorStatus: 403, status: null }),
    "Base contábil: sem acesso à sincronização desta empresa",
  )
  assert.equal(baseStatusText({ hasCompany: false, loading: false, errorStatus: null, status: null }), "Base contábil: escolha uma empresa")
})

test("menu search ignores accents and case and matches group and subgroup", () => {
  assert.equal(menuMatches("", "Cockpit de Fechamento", "Central de Fechamento", "Rotina do mês"), true)
  assert.equal(menuMatches("periodos", "Períodos Congelados", "Central de Fechamento", "Qualidade da base"), true)
  assert.equal(menuMatches("ROTINA", "Cockpit de Fechamento", "Central de Fechamento", "Rotina do mês"), true)
  assert.equal(menuMatches("lalur", "Cockpit de Fechamento", "Central de Fechamento", "Rotina do mês"), false)
})

test("the prototype stylesheet is scoped to the accounting area without renaming classes", () => {
  assert.equal(scopeSelector(":root"), ".ctbp")
  assert.equal(scopeSelector("html"), ".ctbp")
  assert.equal(scopeSelector("body.imprimindo-pk .side"), ".ctbp.imprimindo-pk .side")
  assert.equal(scopeSelector(".app.closed .side"), ".ctbp .app.closed .side")
  assert.equal(scopeSelector("table td.n"), ".ctbp table td.n")
  assert.equal(scopeSelector("html:has(body.imp-x)"), ".ctbp.imp-x")

  const scoped = scopeCss(":root{--bg:#001044}html,body{margin:0}.pill.on{color:red}@keyframes sp{to{opacity:1}}")
  assert.match(scoped, /\.ctbp\{--bg:#001044\}/)
  assert.match(scoped, /\.ctbp\{margin:0\}/)
  assert.match(scoped, /\.ctbp \.pill\.on\{color:red\}/)
  assert.match(scoped, /@keyframes sp\{to\{opacity:1\}\}/)

  const generated = readFileSync(new URL("../src/styles/accounting-prototype.css", import.meta.url), "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
  const selectors = [...generated.matchAll(/(^|\})\s*([^{}@]+)\{/g)]
    .map((match) => match[2].trim())
    .filter((selector) => selector && !/^(from|to|\d+%)/.test(selector))

  for (const list of selectors) {
    for (const selector of list.split(",")) {
      assert.ok(selector.trim().startsWith(".ctbp"), `unscoped selector: ${selector}`)
    }
  }
})

test("accounting caches are partitioned by the session user and share one root for logout", async () => {
  const { ACCOUNTING_AREA_QUERY_ROOT, accountingQueryKey } = await import("../src/features/accounting-area/lib/query-keys.ts")

  assert.deepEqual(accountingQueryKey("u1", "portfolio"), [ACCOUNTING_AREA_QUERY_ROOT, "u1", "portfolio"])
  assert.notDeepEqual(accountingQueryKey("u1", "portfolio"), accountingQueryKey("u2", "portfolio"))

  // Toda chave da área passa pelo helper (nenhuma chave solta sem o usuário).
  const hooks = readFileSync(new URL("../src/features/accounting-area/hooks/use-accounting-area-queries.ts", import.meta.url), "utf8")
  assert.doesNotMatch(hooks, /queryKey:\s*\[/)
})

test("exact decimals are formatted in pt-BR without rounding", async () => {
  const { formatExactDecimal, isNegativeDecimal } = await import("../src/features/accounting-area/lib/exact-decimal.ts")

  assert.equal(formatExactDecimal("1250.5000"), "1.250,50")
  assert.equal(formatExactDecimal("100.0001"), "100,0001")
  assert.equal(formatExactDecimal("-200.2002"), "-200,2002")
  assert.equal(formatExactDecimal("0.0000"), "0,00")
  assert.equal(formatExactDecimal("-0.0000"), "0,00")
  assert.equal(formatExactDecimal("1234567.1"), "1.234.567,10")
  assert.equal(isNegativeDecimal("-0.0000"), false)
  assert.equal(isNegativeDecimal("-0.0100"), true)
})

test("drilldown period windows end on the last day of the month", async () => {
  const { lastDayOf, monthWindow } = await import("../src/features/accounting-area/lib/accounting-period.ts")

  assert.equal(lastDayOf("2026-02"), "2026-02-28")
  assert.equal(lastDayOf("2028-02"), "2028-02-29")
  assert.deepEqual(monthWindow("2026-09", 12), { startDate: "2025-10-01", endDate: "2026-09-30" })
  assert.deepEqual(monthWindow("2026-01", 1), { startDate: "2026-01-01", endDate: "2026-01-31" })
})

test("the monthly DRE only places backend totals and never computes them", async () => {
  const { buildDreMatrix, dreCoverageUntil, trailingMonths } = await import("../src/features/accounting-area/lib/dre-matrix.ts")
  const dre = (groups, until = "2026-09-30") => ({
    unclassifiedAccountCount: 0,
    classifiedTotal: "0.0000",
    coverage: [{ accountingSourceId: "s1", syncedStartDate: "2026-01-01", syncedEndDate: until }],
    groups,
  })
  const ebitda = (revenue, energy) => ({
    dreClassification: "EBITDA",
    total: "999.0000", // total do backend, nunca recalculado
    groups: [
      { dreOpenClassification: "Receita bruta", total: revenue, lines: [{ ebitdaClassification: "Serviços", total: revenue }] },
      ...(energy ? [{ dreOpenClassification: "Despesas", total: energy, lines: [{ ebitdaClassification: "Energia", total: energy }] }] : []),
    ],
  })

  const rows = buildDreMatrix([dre([ebitda("10.0000", "-2.0000")]), null, dre([ebitda("30.0000")])])

  assert.deepEqual(rows.map((row) => [row.kind, row.label]), [
    ["group", "EBITDA"], ["open", "Receita bruta"], ["line", "Serviços"], ["open", "Despesas"], ["line", "Energia"],
  ])
  assert.deepEqual(rows[0].values, ["999.0000", null, "999.0000"])
  assert.deepEqual(rows[4].values, ["-2.0000", null, null])
  assert.equal(dreCoverageUntil(dre([])), "2026-09-30")
  assert.equal(dreCoverageUntil({ coverage: [{ syncedEndDate: null }] }), null)
  assert.deepEqual(trailingMonths("2026-02", 3), ["2025-12", "2026-01", "2026-02"])
})

test("latest-sync coverage only warns about later months and never masks earlier history", async () => {
  const { monthAfterLatestSync } = await import("../src/features/accounting-area/lib/dre-matrix.ts")

  // Coverage is the latest run per source, not cumulative: August imported by an
  // earlier run stays visible even when the latest run covered only September.
  assert.equal(monthAfterLatestSync("2026-08", "2026-09-30"), false)
  assert.equal(monthAfterLatestSync("2026-09", "2026-09-30"), false)
  assert.equal(monthAfterLatestSync("2026-10", "2026-09-30"), true)
  assert.equal(monthAfterLatestSync("2026-10", null), false, "unknown coverage is not claimed")
})

test("a new succeeded run changes the sync signature so data queries refresh", async () => {
  const { succeededSignature } = await import("../src/features/accounting-area/lib/sync-labels.ts")
  const status = (runId) => ({ items: [{ accountingSourceId: "s1", latestSucceededRun: runId ? { id: runId } : null }] })

  assert.equal(succeededSignature(undefined), null)
  assert.notEqual(succeededSignature(status("r1")), succeededSignature(status("r2")))
  assert.equal(succeededSignature(status("r1")), succeededSignature(status("r1")))
  assert.notEqual(succeededSignature(status(null)), succeededSignature(status("r1")))
})
