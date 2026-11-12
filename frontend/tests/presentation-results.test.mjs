import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

test("Apresentação de resultados is published from the existing presentation snapshot", () => {
  const page = readFileSync(
    new URL("../src/features/reports/pages/presentation-results-page.tsx", import.meta.url),
    "utf8",
  )
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  const item = navigationModules
    .find((module) => module.id === "rel")
    .items.find((candidate) => candidate.id === "relApres")

  assert.equal(item.path, "/reports/presentation")
  assert.deepEqual(item.permissions, ["FINANCIAL_ENTRY_VIEW", "FINANCIAL_ENTRY_MANAGE"])
  assert.equal(findNavigationByPath(navigationModules, "/reports/presentation").item.id, "relApres")

  assert.match(router, /path="\/reports\/presentation"[\s\S]*?FINANCIAL_ENTRY_VIEW[\s\S]*?<PresentationResultsPage/)
  assert.match(page, /useDashboardSnapshot/)
  assert.match(page, /dashboardMonthCash/)
  assert.match(page, /indicators\.cash\.generation/)
  assert.match(page, /formatIndicatorPercent/)
  assert.doesNotMatch(page, /\.reduce\(|parseFloat\(|Number\(.*Amount|actualPaidAmount|nominalAmount/)
})
