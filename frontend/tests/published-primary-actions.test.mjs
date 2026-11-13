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
  const toasts = css.match(/\.fin #toasts\{[^}]*z-index:(\d+)/)

  assert.ok(modal)
  assert.ok(tourMask)
  assert.ok(tourBox)
  assert.ok(toasts)

  assert.ok(Number(modal[1]) > Number(tourMask[1]))
  assert.ok(Number(modal[1]) > Number(tourBox[1]))
  assert.ok(Number(toasts[1]) > Number(modal[1]))
})

test("published primary actions use the shared shell modal", () => {
  const paths = [
    "../src/features/billing/pages/service-invoices-page.tsx",
    "../src/features/contracts/pages/contracts-page.tsx",
    "../src/features/debt/pages/debt-origin-page.tsx",
    "../src/features/planning/pages/planning-actions-page.tsx",
    "../src/features/planning/pages/planning-goals-page.tsx",
    "../src/features/planning/pages/planning-meetings-page.tsx",
    "../src/features/sales/pages/sales-activities-page.tsx",
    "../src/features/sales/pages/sales-pipeline-page.tsx",
  ]

  for (const path of paths) {
    const source = readFileSync(new URL(path, import.meta.url), "utf8")
    assert.match(source, /components\/finemp\/modal/)
    assert.match(source, /<Modal/)
    assert.doesNotMatch(source, /className="modal"/)
  }

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

test("shared modal portals into the FINEMP shell", () => {
  const modal = readFileSync(
    new URL("../src/components/finemp/modal.tsx", import.meta.url),
    "utf8",
  )

  assert.match(modal, /createPortal/)
  assert.match(modal, /querySelector<HTMLElement>\("\.fin"\)/)
  assert.match(modal, /createPortal\(modal, shell\)/)
})


test("mutation dialogs keep Escape disabled while a save is pending", () => {
  const mutationPages = [
    "../src/features/billing/pages/service-invoices-page.tsx",
    "../src/features/contracts/pages/contracts-page.tsx",
    "../src/features/debt/pages/debt-origin-page.tsx",
    "../src/features/sales/pages/sales-activities-page.tsx",
    "../src/features/sales/pages/sales-pipeline-page.tsx",
  ]

  for (const path of mutationPages) {
    const source = readFileSync(new URL(path, import.meta.url), "utf8")
    assert.match(source, /onClose=\{save\.isPending \? undefined : onClose\}/)
  }

  const planningPages = [
    "../src/features/planning/pages/planning-actions-page.tsx",
    "../src/features/planning/pages/planning-goals-page.tsx",
    "../src/features/planning/pages/planning-meetings-page.tsx",
  ]

  for (const path of planningPages) {
    const source = readFileSync(new URL(path, import.meta.url), "utf8")
    assert.match(source, /onClose=\{busy \? undefined :/)
  }
})
