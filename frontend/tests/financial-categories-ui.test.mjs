import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  findNavigationByPath,
  navigationModules,
  visibleNavigation,
} from "../src/components/layout/navigation.ts"
import { FINANCIAL_CATEGORY_READ_PERMISSIONS } from "../src/features/financial-categories/lib/financial-category-access.ts"

// Sem stack de renderização: estrutura de menu/rota e garantias verificadas na fonte.
const can = (...permissions) => (permission) => permissions.includes(permission)
const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")

test("the empty state applies the standard plan only on an explicit click", () => {
  const empty = source("features/financial-categories/components/financial-category-empty-state.tsx")
  assert.doesNotMatch(empty, /useEffect|useLayoutEffect/)
  assert.match(empty, /onClick=\{onApplyStandardPlan\}/)
  const page = source("features/financial-categories/pages/financial-categories-page.tsx")
  for (const effect of page.split("useEffect(").slice(1)) {
    assert.doesNotMatch(effect.slice(0, effect.indexOf("}, [")), /applyPlan|standard/i)
  }
})

// 13–14: navegação.
test("cadPlano publishes /financial-categories for VIEW, STRUCTURE_MANAGE or MANAGE; cadCtb is unchanged", () => {
  const cad = navigationModules.find((module) => module.id === "cad")
  const plano = cad.items.find((item) => item.id === "cadPlano")
  assert.equal(plano.label, "Plano de contas")
  assert.equal(plano.path, "/financial-categories")
  assert.deepEqual([...plano.permissions].sort(),
    ["FINANCIAL_CATEGORY_MANAGE", "FINANCIAL_CATEGORY_STRUCTURE_MANAGE", "FINANCIAL_CATEGORY_VIEW"])
  assert.deepEqual(cad.items.find((item) => item.id === "cadCtb"), { id: "cadCtb", label: "Plano de contas da contabilidade" })
  assert.equal(findNavigationByPath(navigationModules, "/financial-categories").item.id, "cadPlano")

  for (const permission of FINANCIAL_CATEGORY_READ_PERMISSIONS) {
    const visible = visibleNavigation(navigationModules, can(permission), () => false)
    assert.deepEqual(visible.map((module) => `${module.id}:${module.items.map((item) => item.id).join(",")}`), ["cad:cadPlano"], permission)
  }
  assert.deepEqual(visibleNavigation(navigationModules, can("FINANCIAL_ENTRY_VIEW"), () => false)
    .flatMap((module) => module.items.map((item) => item.id)).includes("cadPlano"), false)
})

test("the route is protected by the same read permissions", () => {
  const router = source("app/router/app-router.tsx")
  const at = router.indexOf('path="/financial-categories"')
  assert.ok(at > router.indexOf("<Route element={<ProtectedApp />}>"))
  assert.match(router.slice(at, at + 400), /permission=\{FINANCIAL_CATEGORY_READ_PERMISSIONS\}[\s\S]*<FinancialCategoriesPage \/>/)
})

// 15: código nunca é gerado nem editado no frontend.
test("no category code is generated or typed in the frontend", () => {
  for (const file of [
    "components/financial-category-group-dialog.tsx",
    "components/financial-subcategory-dialog.tsx",
    "components/financial-category-rename-dialog.tsx",
    "components/financial-category-classification-dialog.tsx",
    "lib/financial-category-form.ts",
  ]) {
    const text = source(`features/financial-categories/${file}`)
    assert.doesNotMatch(text, /code\s*[:=]\s*[`"']|\.code\s*=|htmlFor="[^"]*[Cc]od|id="[^"]*[Cc]od/, file)
  }
})
