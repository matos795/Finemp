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

test("Pagar e receber is a published Company financial screen", () => {
  const item = navigationModules
    .find((module) => module.id === "fluxo")
    .items.find((candidate) => candidate.id === "agenda")

  assert.equal(item.path, "/cash-flow/schedule")
  assert.deepEqual(item.permissions, ["FINANCIAL_ENTRY_VIEW", "FINANCIAL_ENTRY_MANAGE"])

  const modules = visibleNavigation(
    navigationModules,
    can("FINANCIAL_ENTRY_VIEW"),
    none,
  )
  const fluxo = modules.find((module) => module.id === "fluxo")
  assert.ok(fluxo.items.some((candidate) => candidate.id === "agenda" && candidate.available))

  const current = findNavigationByPath(navigationModules, "/cash-flow/schedule")
  assert.equal(current.module.id, "fluxo")
  assert.equal(current.item.id, "agenda")
})

test("schedule route is permission-protected and reuses open-title settlement", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/reports/pages/cash-flow-schedule-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(router, /path="\/cash-flow\/schedule"[\s\S]*?FINANCIAL_ENTRY_VIEW[\s\S]*?FINANCIAL_ENTRY_MANAGE[\s\S]*?<CashFlowSchedulePage/)
  assert.match(page, /useOpenTitlesList/)
  assert.match(page, /scope: "ALL"/)
  assert.match(page, /FinancialEntrySettlementDialog/)
  assert.match(page, /settlementTargetFromOpenTitle/)
  assert.match(page, /hasPermission\("FINANCIAL_ENTRY_MANAGE"\)/)
  assert.doesNotMatch(page, /fetch\(|apiRequest\(/)
})
