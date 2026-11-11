import { test } from "node:test"
import assert from "node:assert/strict"
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
  for (const placeholder of ["agenda", "bancos", "relApres", "crmFunil", "orc", "bens", "cadPlano"]) {
    assert.equal(all.includes(placeholder), false, placeholder)
  }
})

test("modules left without available items disappear", () => {
  const moduleIds = visibleNavigation(navigationModules, () => true, () => true).map((module) => module.id)

  for (const empty of ["grp", "div", "crm", "pes", "ctr", "est", "pat", "pln", "orc", "ajuda"]) {
    assert.equal(moduleIds.includes(empty), false, empty)
  }
  for (const published of ["fluxo", "rel", "cad", "dem"]) {
    assert.equal(moduleIds.includes(published), true, published)
  }
})

test("a financial client sees the published financial screens it is allowed to open", () => {
  assert.deepEqual(ids(visibleNavigation(navigationModules, FINANCE_CLIENT, none)), [
    "fluxo:painel,lanc,ext,mensal,diario",
    "rel:relRec,relPag,dre",
    "cad:cadContas",
  ])
})

test("Company and Platform permissions keep filtering the available items", () => {
  assert.deepEqual(visibleNavigation(navigationModules, none, none), [])

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, none, can("PLATFORM_USER_MANAGE"))),
    ["admin:adminOnboarding"],
  )

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("BANK_ACCOUNT_VIEW"), none)),
    ["cad:cadContas"],
  )

  assert.deepEqual(
    ids(visibleNavigation(navigationModules, can("DRE_VIEW"), none)),
    ["dem:dreContabil"],
  )
})

test("the roadmap stays declared and the current route is still identified", () => {
  const lanc = navigationModules.find((module) => module.id === "fluxo").items.find((item) => item.id === "agenda")
  assert.equal(lanc.path, undefined)

  const current = findNavigationByPath(navigationModules, "/financial-entries")
  assert.equal(current.module.id, "fluxo")
  assert.equal(current.item.id, "lanc")
  assert.equal(findNavigationByPath(navigationModules, "/reports/receivables").item.id, "relRec")
})

test("only single-screen modules of the prototype become direct links", () => {
  const modules = visibleNavigation(navigationModules, FINANCE_CLIENT, none)
  const cadastros = modules.find((module) => module.id === "cad")
  assert.equal(cadastros.items.length, 1)
  assert.equal(cadastros.standalone, false)
  assert.equal(modules.find((module) => module.id === "fluxo").standalone, false)
})
