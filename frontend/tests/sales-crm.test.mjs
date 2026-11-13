import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createOpportunity,
  getSalesIndicators,
  listSalesPartnerOptions,
} from "../src/features/sales/api/sales-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

test("CRM publishes the four homologated routes with CRM permissions", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  const expected = [
    ["/sales/pipeline", "crmFunil"],
    ["/sales/activities", "crmAtiv"],
    ["/sales/portfolio", "crmCli"],
    ["/sales/indicators", "crmInd"],
  ]

  for (const [path, id] of expected) {
    const item = findNavigationByPath(navigationModules, path).item
    assert.equal(item.id, id)
    assert.deepEqual(item.permissions, ["CRM_VIEW", "CRM_MANAGE"])
    assert.match(router, new RegExp(`path="${path}"`))
  }
})

test("opportunity money remains exact text and partner lookup is CRM-scoped", async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    if (String(path) === "/api/auth/csrf") {
      return Response.json({ headerName: "X-CSRF-TOKEN", token: "test-token" })
    }
    if (String(path).includes("/sales/partners")) {
      return Response.json({
        items: [],
        page: 0,
        size: 50,
        totalItems: 0,
        totalPages: 0,
      })
    }
    return Response.json({
      id: "op-1",
      companyId: "company-1",
      businessPartnerId: null,
      businessPartnerName: null,
      title: "Projeto",
      stage: "Proposta",
      monthlyAmount: "1234.56",
      probability: 50,
      responsible: "Equipe",
      expectedCloseOn: null,
      notes: null,
      status: "OPEN",
      wonAt: null,
      lostAt: null,
      version: 0,
      createdAt: "2026-11-13T00:00:00Z",
      updatedAt: "2026-11-13T00:00:00Z",
      stageHistory: [],
    })
  }

  await createOpportunity("company-1", {
    businessPartnerId: null,
    title: "Projeto",
    stage: "Proposta",
    monthlyAmount: "1234.56",
    probability: 50,
    responsible: "Equipe",
    expectedCloseOn: null,
    notes: null,
  })
  await listSalesPartnerOptions("company-1", "cliente")
  await listSalesPartnerOptions("company-1", "cliente", undefined, true)

  const createCall = calls.find((call) =>
    String(call.path) === "/api/companies/company-1/opportunities"
  )
  const partnerCalls = calls.filter((call) =>
    String(call.path).includes("/sales/partners?")
  )
  assert.ok(createCall)
  assert.equal(partnerCalls.length, 2)

  const body = JSON.parse(createCall.options.body)
  assert.equal(body.monthlyAmount, "1234.56")
  assert.equal(typeof body.monthlyAmount, "string")
  assert.equal(
    partnerCalls[0].path,
    "/api/companies/company-1/sales/partners?page=0&size=50&search=cliente",
  )
  assert.equal(
    partnerCalls[1].path,
    "/api/companies/company-1/sales/partners?page=0&size=50&search=cliente&includeInactive=true",
  )
})

test("sales indicators are read from the backend without frontend financial recomputation", async () => {
  globalThis.fetch = async () =>
    Response.json({
      wonCount: 2,
      lostCount: 1,
      conversionRate: "66.67",
      averageWonTicket: "1500.00",
      averageSalesCycleDays: "15.00",
      weightedOpenPipeline: "300.00",
      annualizedWonValue: "36000.00",
      forecast: [],
    })

  const report = await getSalesIndicators("company-1")
  assert.equal(report.weightedOpenPipeline, "300.00")
  assert.equal(report.annualizedWonValue, "36000.00")

  const page = readFileSync(
    new URL("../src/features/sales/pages/sales-indicators-page.tsx", import.meta.url),
    "utf8",
  )
  assert.doesNotMatch(page, /monthlyAmount\s*[*]\s*probability/)
  assert.doesNotMatch(page, /wonCount\s*[/]\s*/)
})


test("follow-up agenda keeps saved observations visible", () => {
  const page = readFileSync(
    new URL("../src/features/sales/pages/sales-activities-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(page, /activity\.notes/)
  assert.match(page, /className="help"/)
})


test("company access selector exposes CRM permissions", () => {
  const page = readFileSync(
    new URL("../src/features/platform-admin/pages/platform-admin-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(page, /CRM_VIEW/)
  assert.match(page, /CRM_MANAGE/)
})
