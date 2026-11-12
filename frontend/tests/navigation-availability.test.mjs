import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  findNavigationByPath,
  navigationModules,
  visibleNavigation,
} from "../src/components/layout/navigation.ts"

const can = (...permissions) => (permission) => permissions.includes(permission)
const none = () => false

const FINANCE_CLIENT = can(
  "DASHBOARD_VIEW",
  "BANK_ACCOUNT_VIEW",
  "BANK_ACCOUNT_MANAGE",
  "FINANCIAL_CATEGORY_VIEW",
  "FINANCIAL_ENTRY_VIEW",
  "FINANCIAL_ENTRY_MANAGE",
)

const ids = (modules) => modules.map((module) => `${module.id}:${module.items.map((item) => item.id).join(",")}`)

test("items without a published route are not shown", () => {
  const modules = visibleNavigation(navigationModules, () => true, () => true)

  for (const module of modules) {
    for (const item of module.items) {
      assert.equal(typeof item.path, "string", `${module.id}/${item.id}`)
    }
  }

  const all = modules.flatMap((module) => module.items.map((item) => item.id))
  for (const placeholder of ["crmFunil", "orc", "bens"]) {
    assert.equal(all.includes(placeholder), false, placeholder)
  }
})

test("modules left without available items disappear", () => {
  const moduleIds = visibleNavigation(navigationModules, () => true, () => true).map((module) => module.id)

  for (const empty of ["grp", "div", "crm", "pes", "ctr", "est", "pat", "pln", "orc"]) {
    assert.equal(moduleIds.includes(empty), false, empty)
  }
  for (const published of ["fluxo", "rel", "cad", "ajuda", "dem"]) {
    assert.equal(moduleIds.includes(published), true, published)
  }
})

test("a financial client sees the published financial screens it is allowed to open", () => {
  assert.deepEqual(ids(visibleNavigation(navigationModules, FINANCE_CLIENT, none)), [
    "fluxo:painel,lanc,agenda,ext,mensal,diario,bancos",
    "rel:relRec,relPag,dre,relApres",
    "cad:empresas,cadContas,cadPlano",
    "ajuda:faq,tutor",
  ])
})

test("Company and Platform permissions keep filtering the available items", () => {
  assert.deepEqual(
    ids(visibleNavigation(navigationModules, none, none)),
    ["cad:empresas", "ajuda:faq,tutor"],
  )

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, none, can("PLATFORM_USER_MANAGE"))),
    ["admin:adminOnboarding,admUsers", "cad:empresas", "ajuda:faq,tutor"],
  )

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("BANK_ACCOUNT_VIEW"), none)),
    ["cad:empresas,cadContas", "ajuda:faq,tutor"],
  )

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("DRE_VIEW"), none)),
    ["cad:empresas", "ajuda:faq,tutor", "dem:dreContabil"],
  )
})

test("the roadmap stays declared and the current route is still identified", () => {
  const agenda = navigationModules.find((module) => module.id === "fluxo").items.find((item) => item.id === "agenda")
  assert.equal(agenda.path, "/cash-flow/schedule")

  const current = findNavigationByPath(navigationModules, "/financial-entries")
  assert.equal(current.module.id, "fluxo")
  assert.equal(current.item.id, "lanc")
  assert.equal(findNavigationByPath(navigationModules, "/reports/receivables").item.id, "relRec")
})

test("only single-screen modules of the prototype become direct links", () => {
  const modules = visibleNavigation(navigationModules, FINANCE_CLIENT, none)
  const cadastros = modules.find((module) => module.id === "cad")
  assert.equal(cadastros.items.length, 3)
  assert.equal(cadastros.standalone, false)
  assert.equal(modules.find((module) => module.id === "fluxo").standalone, false)
})

test("Clientes e fornecedores is published for BUSINESS_PARTNER_VIEW or MANAGE only", () => {
  const item = navigationModules.find((module) => module.id === "cad").items.find((candidate) => candidate.id === "parc")
  assert.equal(item.path, "/business-partners")
  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("BUSINESS_PARTNER_VIEW"), none)),
    ["cad:empresas,parc", "ajuda:faq,tutor"],
  )
  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("BUSINESS_PARTNER_MANAGE", "BANK_ACCOUNT_VIEW"), none)),
    ["cad:empresas,parc,cadContas", "ajuda:faq,tutor"],
  )
  assert.equal(findNavigationByPath(navigationModules, "/business-partners").item.id, "parc")
})


test("SYSTEM_ADMIN sees the declared roadmap without synthetic routes", () => {
  const modules = visibleNavigation(navigationModules, () => true, () => true, true)
  const all = modules.flatMap((module) => module.items)

  for (const id of ["crmFunil", "orc", "bens"]) {
    const item = all.find((candidate) => candidate.id === id)
    assert.ok(item, id)
    assert.equal(item.available, false, id)
    assert.equal(item.path, undefined, id)
  }

  for (const item of all.filter((candidate) => candidate.path !== undefined)) {
    assert.equal(item.available, true, item.id)
  }
})


test("Usuários e acessos stays published after the availability batch", () => {
  const item = navigationModules
    .find((module) => module.id === "admin")
    .items.find((candidate) => candidate.id === "admUsers")

  assert.equal(item.path, "/admin/users")
  assert.deepEqual(item.platformPermissions, ["PLATFORM_USER_MANAGE"])
  assert.equal(findNavigationByPath(navigationModules, "/admin/users").item.id, "admUsers")
})

test("availability batch routes are published with their real permission boundaries", () => {
  assert.equal(findNavigationByPath(navigationModules, "/admin/clients").item.id, "admClientes")
  assert.equal(findNavigationByPath(navigationModules, "/result-centers").item.id, "cr")
  assert.equal(findNavigationByPath(navigationModules, "/financial-periods").item.id, "perFech")
  assert.equal(findNavigationByPath(navigationModules, "/help/faq").item.id, "faq")
  assert.equal(findNavigationByPath(navigationModules, "/help/tutorials").item.id, "tutor")

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("RESULT_CENTER_VIEW"), none)),
    ["cad:empresas,cr", "ajuda:faq,tutor"],
  )
  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("FINANCIAL_PERIOD_VIEW"), none)),
    ["cad:empresas", "ajuda:faq,tutor", "dem:perFech"],
  )
  assert.deepEqual(
    ids(visibleNavigation(navigationModules, none, can("ACCOUNTING_FIRM_VIEW"))),
    ["admin:adminOnboarding,admClientes", "cad:empresas", "ajuda:faq,tutor"],
  )
})

test("accounting read routes are published with distinct permissions", () => {
  assert.equal(
    findNavigationByPath(navigationModules, "/accounting-accounts").item.id,
    "cadCtb",
  )
  assert.equal(
    findNavigationByPath(navigationModules, "/accounting-movements").item.id,
    "ctb",
  )

  assert.deepEqual(
    ids(visibleNavigation(
      navigationModules,
      can("ACCOUNT_CLASSIFICATION_VIEW"),
      none,
    )),
    ["cad:empresas,cadCtb", "ajuda:faq,tutor"],
  )

  assert.deepEqual(
    ids(visibleNavigation(
      navigationModules,
      can("ACCOUNTING_MOVEMENT_VIEW"),
      none,
    )),
    ["cad:empresas", "ajuda:faq,tutor", "dem:ctb"],
  )
})

test("Empresas is published for every authenticated user without granting access", () => {
  const item = navigationModules
    .find((module) => module.id === "cad")
    .items.find((candidate) => candidate.id === "empresas")

  assert.equal(item.path, "/companies")
  assert.equal(item.permissions, undefined)
  assert.equal(item.platformPermissions, undefined)
  assert.equal(
    findNavigationByPath(navigationModules, "/companies").item.id,
    "empresas",
  )
})

test("audit routes are visible only with SYSTEM_ADMIN", () => {
  assert.equal(
    findNavigationByPath(navigationModules, "/admin/activity-log").item.id,
    "admLog",
  )
  assert.equal(
    findNavigationByPath(navigationModules, "/audit-trail").item.id,
    "audit",
  )

  assert.equal(
    visibleNavigation(
      navigationModules,
      () => false,
      (permission) => permission === "PLATFORM_USER_MANAGE",
    )
      .flatMap((module) => module.items)
      .some((item) => item.id === "admLog" || item.id === "audit"),
    false,
  )

  const systemAdmin = visibleNavigation(
    navigationModules,
    () => false,
    (permission) => permission === "SYSTEM_ADMIN",
  ).flatMap((module) => module.items.map((item) => item.id))

  assert.equal(systemAdmin.includes("admLog"), true)
  assert.equal(systemAdmin.includes("audit"), true)
})

test("normal users still receive only published and authorized items", () => {
  const normal = visibleNavigation(navigationModules, FINANCE_CLIENT, none)
  const roadmap = normal.flatMap((module) => module.items).filter((item) => !item.available)
  assert.deepEqual(roadmap, [])

  const publishedIds = navigationModules
    .flatMap((module) => module.items)
    .filter((item) => item.path !== undefined)
    .map((item) => item.id)

  const privileged = visibleNavigation(navigationModules, () => true, () => true)
    .flatMap((module) => module.items)
    .map((item) => item.id)

  for (const id of publishedIds) assert.ok(privileged.includes(id), id)
})

test("roadmap mode preserves module order and declares every reference screen", () => {
  assert.deepEqual(navigationModules.map((module) => module.id), [
    "admin", "fluxo", "rel", "grp", "div", "crm", "pes", "ctr",
    "est", "pat", "pln", "orc", "cad", "ajuda", "dem",
  ])

  const reference = JSON.parse(readFileSync(
    new URL("../../docs/reference/reference-navigation.json", import.meta.url),
    "utf8",
  ))
  const declared = new Set(navigationModules.flatMap((module) => module.items.map((item) => item.id)))

  for (const screen of reference.screens) {
    assert.ok(declared.has(screen.id), `missing reference screen: ${screen.id}`)
  }
})


test("sidebar renders roadmap items disabled instead of links", () => {
  const sidebar = readFileSync(
    new URL("../src/components/layout/app-sidebar.tsx", import.meta.url),
    "utf8",
  )

  assert.match(sidebar, /item\.available \? \([\s\S]*?<NavLink/)
  assert.match(sidebar, /className="mi roadmap-soon"[\s\S]*?aria-disabled="true"/)
  assert.match(sidebar, /roadmap-soon-badge">Em breve</)
  assert.match(sidebar, /platformPermissions \?\? \[\]\)\.includes\("SYSTEM_ADMIN"\)/)

  const unavailableBranch = sidebar.slice(
    sidebar.indexOf('className="mi roadmap-soon"') - 120,
    sidebar.indexOf('className="mi roadmap-soon"') + 420,
  )
  assert.doesNotMatch(unavailableBranch, /<NavLink|\bto=|onClick=/)
})

test("roadmap styling lives in overrides, not the generated theme", () => {
  const overrides = readFileSync(
    new URL("../src/styles/finemp-overrides.css", import.meta.url),
    "utf8",
  )
  const theme = readFileSync(
    new URL("../src/styles/finemp-theme.css", import.meta.url),
    "utf8",
  )

  assert.match(overrides, /\.fin \.roadmap-soon/)
  assert.match(overrides, /\.fin \.roadmap-soon-badge/)
  assert.doesNotMatch(theme, /roadmap-soon/)
})


test("Contas e saldos requires a bank permission and a financial-entry permission", () => {
  const item = navigationModules
    .find((module) => module.id === "fluxo")
    .items.find((candidate) => candidate.id === "bancos")

  assert.equal(item.path, "/cash-flow/accounts")
  assert.deepEqual(item.permissionGroups, [
    ["BANK_ACCOUNT_VIEW", "BANK_ACCOUNT_MANAGE"],
    ["FINANCIAL_ENTRY_VIEW", "FINANCIAL_ENTRY_MANAGE"],
  ])

  assert.equal(
    visibleNavigation(navigationModules, can("BANK_ACCOUNT_VIEW"), none)
      .flatMap((module) => module.items)
      .some((candidate) => candidate.id === "bancos"),
    false,
  )

  assert.equal(
    visibleNavigation(
      navigationModules,
      can("BANK_ACCOUNT_VIEW", "FINANCIAL_ENTRY_VIEW"),
      none,
    )
      .flatMap((module) => module.items)
      .some((candidate) => candidate.id === "bancos"),
    true,
  )

  assert.equal(findNavigationByPath(navigationModules, "/cash-flow/accounts").item.id, "bancos")
})
