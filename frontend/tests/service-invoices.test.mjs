import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  cancelServiceInvoice,
  listServiceInvoices,
  markServiceInvoiceIssued,
} from "../src/features/billing/api/service-invoices-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("Notas fiscais is published with dedicated billing permissions", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/billing/pages/service-invoices-page.tsx", import.meta.url),
    "utf8",
  )

  const item = findNavigationByPath(
    navigationModules,
    "/clients/service-invoices",
  ).item

  assert.equal(item.id, "nf")
  assert.deepEqual(item.permissions, ["BILLING_VIEW", "BILLING_MANAGE"])
  assert.match(
    router,
    /path="\/clients\/service-invoices"[\s\S]*?<ServiceInvoicesPage/,
  )
  assert.match(page, /totalWithheld/)
  assert.match(page, /netAmount/)
  assert.doesNotMatch(
    page,
    /grossAmount\s*[+\-*/]|issAmount\s*[+\-*/]|totalWithheld\s*[+\-*/]|parseFloat|Number\(\s*invoice\.(?:grossAmount|totalWithheld|netAmount)/,
  )
  assert.match(page, /não emite NFS-e/i)

  const onboarding = readFileSync(
    new URL("../src/features/platform-admin/components/official-user-onboarding-section.tsx", import.meta.url),
    "utf8",
  )
  const selfAccess = readFileSync(
    new URL("../src/features/platform-admin/pages/platform-admin-page.tsx", import.meta.url),
    "utf8",
  )

  for (const source of [onboarding, selfAccess]) {
    assert.match(source, /BILLING_VIEW/)
    assert.match(source, /BILLING_MANAGE/)
  }
})

test("invoice API keeps month, status and optimistic version explicit", async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })

    if (String(path) === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "test-token",
      })
    }

    return Response.json({
      items: [],
      page: 0,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await listServiceInvoices(
    "company-1",
    "2026-11",
    "DRAFT",
    "cliente",
    0,
  )

  const invoice = {
    id: "invoice-1",
    version: 7,
  }

  await markServiceInvoiceIssued(
    "company-1",
    invoice,
    "12345",
  )

  await cancelServiceInvoice(
    "company-1",
    invoice,
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/service-invoices?month=2026-11&page=0&size=50&status=DRAFT&search=cliente",
  )
  const issuance = calls.find((call) =>
    String(call.path).endsWith("/issuance")
  )
  const cancellation = calls.find((call) =>
    String(call.path).endsWith("/cancellation")
  )

  assert.ok(issuance)
  assert.ok(cancellation)

  assert.deepEqual(
    JSON.parse(issuance.options.body),
    { expectedVersion: 7, invoiceNumber: "12345" },
  )
  assert.deepEqual(
    JSON.parse(cancellation.options.body),
    { expectedVersion: 7, invoiceNumber: null },
  )
})
