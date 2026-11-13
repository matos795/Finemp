import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createContract,
  listContractPartnerOptions,
  listContracts,
  updateContract,
} from "../src/features/contracts/api/contracts-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("contract list keeps filters on the server", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      items: [],
      page: 0,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await listContracts(
    "company-1",
    { search: "  mensalidade  ", active: "true" },
    2,
  )

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/contracts?page=2&size=50&search=mensalidade&active=true",
  )
  assert.equal(calls[0].options.credentials, "include")
})

test("contract partner picker uses the contract-scoped lookup", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      items: [],
      page: 0,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await listContractPartnerOptions("company-1", " cliente ")

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/contracts/partners?page=0&size=50&search=cliente",
  )
})

test("contract writes preserve exact money as text and optimistic version", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      id: "contract-1",
      companyId: "company-1",
      businessPartnerId: "partner-1",
      businessPartnerName: "Cliente",
      direction: "INCOME",
      description: "Mensalidade",
      startDate: "2026-01-01",
      endDate: null,
      billingDay: 10,
      monthlyAmount: "1234.56",
      active: true,
      version: 3,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    })
  }

  const values = {
    businessPartnerId: "partner-1",
    direction: "INCOME",
    description: "Mensalidade",
    startDate: "2026-01-01",
    endDate: null,
    billingDay: 10,
    monthlyAmount: "1234.56",
    active: true,
  }

  await createContract("company-1", values)
  await updateContract(
    "company-1",
    {
      id: "contract-1",
      companyId: "company-1",
      businessPartnerId: "partner-1",
      businessPartnerName: "Cliente",
      direction: "INCOME",
      description: "Mensalidade",
      startDate: "2026-01-01",
      endDate: null,
      billingDay: 10,
      monthlyAmount: "1234.56",
      active: true,
      version: 2,
      createdAt: "",
      updatedAt: "",
    },
    values,
  )

  const writeCalls = calls.filter((call) => call.path !== "/api/auth/csrf")
  const createBody = JSON.parse(writeCalls[0].options.body)
  const updateBody = JSON.parse(writeCalls[1].options.body)

  assert.equal(typeof createBody.monthlyAmount, "string")
  assert.equal(createBody.monthlyAmount, "1234.56")
  assert.equal(createBody.expectedVersion, null)

  assert.equal(typeof updateBody.monthlyAmount, "string")
  assert.equal(updateBody.expectedVersion, 2)
})

test("Contratos e reajustes is a real permission-guarded route", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/contracts/pages/contracts-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(router, /path="\/contracts"/)
  assert.match(
    router,
    /permission=\{\["CONTRACT_VIEW", "CONTRACT_MANAGE"\]\}/,
  )
  assert.match(
    navigation,
    /id: "ctr"[\s\S]*?path: "\/contracts"[\s\S]*?CONTRACT_VIEW[\s\S]*?CONTRACT_MANAGE/,
  )

  assert.match(page, /className="filters"/)
  assert.match(page, /className="tblwrap"/)
  assert.match(page, /className="modal"/)
  assert.doesNotMatch(page, /Number\(monthlyAmount\)/)
  assert.match(page, /listContractPartnerOptions/)
  assert.match(page, /selectedPartnerMissing/)
  assert.match(page, /CONTRACT_REFERENCED_PARTNER_CHANGE/)
  assert.match(page, /nota fiscal vinculada/i)
  assert.match(page, /invalidateQueries\(\{[\s\S]*?queryKey: \["contracts", companyId\]/)
})
