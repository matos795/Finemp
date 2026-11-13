import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { getReceivedPaymentsByMethod } from "../src/features/reports/api/reports-api.ts"
import {
  findNavigationByPath,
  navigationModules,
  visibleNavigation,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

const can = (...permissions) => (permission) => permissions.includes(permission)
const none = () => false

test("Clientes publishes delinquency and receipts by method with financial read permissions", () => {
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
    /id: "ctrInad"[\s\S]*?path: "\/clients\/delinquency"[\s\S]*?FINANCIAL_ENTRY_VIEW[\s\S]*?FINANCIAL_ENTRY_MANAGE/,
  )
  assert.match(
    navigation,
    /id: "ctrFormas"[\s\S]*?path: "\/clients\/receipts-by-method"[\s\S]*?FINANCIAL_ENTRY_VIEW[\s\S]*?FINANCIAL_ENTRY_MANAGE/,
  )
  assert.match(router, /path="\/clients\/delinquency"/)
  assert.match(router, /<OpenTitlesPage direction="INCOME" \/>/)
  assert.match(router, /path="\/clients\/receipts-by-method"/)
  assert.match(router, /<ReceivedPaymentsByMethodPage \/>/)

  assert.equal(
    findNavigationByPath(navigationModules, "/clients/delinquency").item.id,
    "ctrInad",
  )
  assert.equal(
    findNavigationByPath(navigationModules, "/clients/receipts-by-method").item.id,
    "ctrFormas",
  )

  const visible = visibleNavigation(
    navigationModules,
    can("FINANCIAL_ENTRY_VIEW"),
    none,
  ).flatMap((module) => module.items.map((item) => item.id))

  assert.ok(visible.includes("ctrInad"))
  assert.ok(visible.includes("ctrFormas"))
})

test("received payments by method uses only company and inclusive period query params", async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      from: "2026-11-01",
      to: "2026-11-30",
      totalCount: 0,
      totalAmount: "0.00",
      methods: [],
    })
  }

  const result = await getReceivedPaymentsByMethod(
    "company-1",
    "2026-11-01",
    "2026-11-30",
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/financial-reports/received-by-method?from=2026-11-01&to=2026-11-30",
  )
  assert.equal(calls[0].options.credentials, "include")
  assert.equal(result.totalAmount, "0.00")
})
