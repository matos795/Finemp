import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("availability batch routes use real pages and guards", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  for (const route of [
    "/admin/clients",
    "/result-centers",
    "/financial-periods",
    "/help/faq",
    "/help/tutorials",
  ]) {
    assert.match(router, new RegExp(`path="${route.replaceAll("/", "\\/")}"`))
  }

  assert.match(router, /permission="ACCOUNTING_FIRM_VIEW"/)
  assert.match(router, /RESULT_CENTER_VIEW/)
  assert.match(router, /FINANCIAL_PERIOD_VIEW/)
})

test("Clientes FINEMP stays read-only in its dedicated page", () => {
  const page = readFileSync(
    new URL("../src/features/platform-admin/pages/platform-clients-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(page, /somente de consulta/)
  assert.doesNotMatch(page, /createClient|createCompany|deleteClient/)
})

test("help pages describe only published workflows", () => {
  const faq = readFileSync(
    new URL("../src/features/help/pages/faq-page.tsx", import.meta.url),
    "utf8",
  )
  const tutorials = readFileSync(
    new URL("../src/features/help/pages/tutorials-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(faq, /Lançamento/)
  assert.match(faq, /Questor\/Firebird/)
  assert.match(tutorials, /Conciliar/)
  assert.match(tutorials, /Estornar uma baixa/)
})
