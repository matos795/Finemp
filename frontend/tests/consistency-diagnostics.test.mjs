import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("consistency diagnostics is a real permission-group guarded route", () => {
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  assert.match(
    navigation,
    /id: "consist"[\s\S]*?path: "\/reports\/consistency"[\s\S]*?CONTRACT_VIEW[\s\S]*?CONTRACT_MANAGE[\s\S]*?BILLING_VIEW[\s\S]*?BILLING_MANAGE/,
  )
  assert.match(router, /path="\/reports\/consistency"/)
  assert.match(
    router,
    /path="\/reports\/consistency"[\s\S]*?<RequirePermissionGroups[\s\S]*?CONTRACT_VIEW[\s\S]*?BILLING_VIEW/,
  )
})

test("consistency diagnostics reads the backend and keeps partial coverage explicit", () => {
  const api = readFileSync(
    new URL("../src/features/reports/api/reports-api.ts", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/reports/pages/consistency-diagnostics-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(
    api,
    /getConsistencyDiagnostics[\s\S]*?\/diagnostics/,
  )
  assert.match(api, /cache: "no-store"/)

  assert.match(page, /Cobertura parcial/)
  assert.match(page, /não altera nem corrige dados automaticamente/)
  assert.match(page, /occurrenceCount/)
  assert.match(page, /occurrenceIds/)
  assert.doesNotMatch(page, /reduce\(/)
  assert.doesNotMatch(page, /BigInt|BigNumber|parseFloat|Number\(/)
})

test("consistency page names the domains that are deliberately still outside coverage", () => {
  const page = readFileSync(
    new URL("../src/features/reports/pages/consistency-diagnostics-page.tsx", import.meta.url),
    "utf8",
  )

  for (const label of ["NCG", "Balanço", "Estoque", "Bens"]) {
    assert.ok(page.includes(label), label)
  }
})
