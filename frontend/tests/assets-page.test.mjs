import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createAsset,
  getAssetSummary,
  listAssets,
  updateAsset,
} from "../src/features/assets/api/assets-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"
import {
  listAllResultCenters,
} from "../src/features/result-centers/api/result-center-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("asset route is published only for asset permissions", () => {
  const item = findNavigationByPath(navigationModules, "/assets").item

  assert.equal(item.id, "bens")
  assert.deepEqual(item.permissions, ["ASSET_VIEW", "ASSET_MANAGE"])

  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  assert.match(
    router,
    /path="\/assets"[\s\S]*?ASSET_VIEW[\s\S]*?ASSET_MANAGE[\s\S]*?<AssetsPage/,
  )
})

test("asset reads keep filters pagination and summary on the server", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (String(path).endsWith("/summary")) {
      return Response.json({
        activeCount: 0,
        acquisitionValue: "0.00",
        bookValue: "0.00",
        monthlyDepreciation: "0.00",
      })
    }

    return Response.json({
      items: [],
      page: 2,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await listAssets(
    "company-1",
    {
      search: " notebook ",
      status: "IN_USE",
      category: "IT",
    },
    2,
  )
  await getAssetSummary("company-1")

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/assets?page=2&size=50&search=notebook&status=IN_USE&category=IT",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/assets/summary",
  )
  assert.equal(calls[0].options.cache, "no-store")
  assert.equal(calls[1].options.cache, "no-store")
})

test("asset commands send money as strings and optimistic version only on update", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "csrf-token",
      })
    }

    return Response.json({
      id: "asset-1",
      version: 4,
    })
  }

  const values = {
    assetTag: "PAT-0001",
    description: "Notebook sintético",
    category: "IT",
    brandModel: null,
    serialNumber: null,
    acquisitionDate: "2026-01-10",
    acquisitionValue: "1200.00",
    quantity: 1,
    businessPartnerId: null,
    documentReference: null,
    resultCenterId: null,
    location: null,
    responsible: null,
    usefulLifeMonths: 60,
    residualPercent: "0.00",
    depreciable: true,
    depreciationStartDate: null,
    status: "IN_USE",
    warrantyUntil: null,
    notes: null,
  }

  await createAsset("company-1", values)
  await updateAsset(
    "company-1",
    { id: "asset-1", version: 3 },
    values,
  )

  const writes = calls.filter((call) =>
    call.path !== "/api/auth/csrf" &&
    call.options.method,
  )

  const createBody = JSON.parse(writes[0].options.body)
  const updateBody = JSON.parse(writes[1].options.body)

  assert.equal(createBody.acquisitionValue, "1200.00")
  assert.equal(createBody.residualPercent, "0.00")
  assert.equal(createBody.expectedVersion, null)
  assert.equal(updateBody.expectedVersion, 3)
})

test("asset result-center lookup walks every server page", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    const url = new URL(path, "https://example.invalid")
    const page = Number(url.searchParams.get("page"))

    return Response.json({
      items: [{
        id: "center-" + page,
        companyId: "company-1",
        parentId: null,
        code: String(page + 1),
        name: "Centro " + page,
        active: true,
        hasChildren: false,
        version: 0,
        type: "ANALYTICAL",
      }],
      page,
      size: 100,
      totalItems: 2,
      totalPages: 2,
    })
  }

  const result = await listAllResultCenters("company-1")

  assert.deepEqual(
    result.map((center) => center.id),
    ["center-0", "center-1"],
  )
  assert.equal(calls.length, 2)
  assert.equal(
    calls[0].path,
    "/api/companies/company-1/result-centers?search=&page=0&size=100",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/result-centers?search=&page=1&size=100",
  )
})

test("asset page never recomputes depreciation or book value in React", () => {
  const source = readFileSync(
    new URL("../src/features/assets/pages/assets-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(source, /monthlyDepreciation/)
  assert.match(source, /accumulatedDepreciation/)
  assert.match(source, /bookValue/)
  assert.doesNotMatch(source, /depMensal|depreciableBase|Math\.pow|\.reduce\(/)
  assert.match(source, /\+ Bem/)
  assert.match(source, /Editar/)
  assert.doesNotMatch(source, /Manutenção|Vender\/baixar/)
})
