import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("action modals render above the onboarding tour", () => {
  const css = readFileSync(
    new URL("../src/styles/finemp-theme.css", import.meta.url),
    "utf8",
  )

  const modal = css.match(/\.fin \.modal\{[^}]*z-index:(\d+)/)
  const tourMask = css.match(/\.fin #tourMask\{[^}]*z-index:(\d+)/)
  const tourBox = css.match(/\.fin #tourBox\{[^}]*z-index:(\d+)/)

  assert.ok(modal)
  assert.ok(tourMask)
  assert.ok(tourBox)

  assert.ok(Number(modal[1]) > Number(tourMask[1]))
  assert.ok(Number(modal[1]) > Number(tourBox[1]))
})

test("published invoice and opportunity buttons still open real dialog state", () => {
  const invoices = readFileSync(
    new URL("../src/features/billing/pages/service-invoices-page.tsx", import.meta.url),
    "utf8",
  )
  const sales = readFileSync(
    new URL("../src/features/sales/pages/sales-pipeline-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(
    invoices,
    /onClick=\{\(\) => setEdit\("new"\)\}[\s\S]*?\+ Preparar nota/,
  )
  assert.match(invoices, /edit && companyId[\s\S]*?<InvoiceDraftDialog/)

  assert.match(
    sales,
    /onClick=\{\(\) => setDialog\(\{\}\)\}[\s\S]*?\+ Oportunidade/,
  )
  assert.match(sales, /dialog && companyId[\s\S]*?<OpportunityDialog/)
})
