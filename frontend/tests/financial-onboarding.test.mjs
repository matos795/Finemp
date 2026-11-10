import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { applyStandardFinancialCategoryPlan } from "../src/features/financial-entries/api/financial-catalogs-api.ts"
import {
  describeStandardPlanFailure,
  financialOnboarding,
  noticeForCompany,
  refreshFinancialCategories,
  standardPlanSuccessMessage,
} from "../src/features/financial-entries/lib/financial-onboarding.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const COMPANY_A = "c0000000-0000-4000-8000-00000000000a"
const COMPANY_B = "c0000000-0000-4000-8000-00000000000b"

const can = (...permissions) => (permission) => permissions.includes(permission)
const ready = (items) => ({ status: "ready", items })
const ACTIVE_ACCOUNT = [{ active: true }]
const PLAN = [{ kind: "GROUP" }, { kind: "SUBCATEGORY" }]

test("standard plan is an explicit POST through the shared client with CSRF", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json({ version: "FINEMP_WEB_1", groups: 11, subcategories: 60 }, { status: 201 })
  }

  const result = await applyStandardFinancialCategoryPlan(COMPANY_A)

  const command = calls.find((call) => call.path !== "/api/auth/csrf")
  assert.equal(command.path, `/api/companies/${COMPANY_A}/financial-categories/standard-plan`)
  assert.equal(command.options.method, "POST")
  assert.equal(command.options.credentials, "include")
  assert.equal(new Headers(command.options.headers).get("X-XSRF-TOKEN"), "t")
  assert.equal(command.options.body, undefined)
  assert.equal(calls.filter((call) => call.path !== "/api/auth/csrf").length, 1)
  assert.equal(standardPlanSuccessMessage(result), "Plano padrão FINEMP aplicado: 11 grupos e 60 subcategorias criados.")
})

test("empty catalog offers the standard plan only with FINANCIAL_CATEGORY_MANAGE", () => {
  const manager = financialOnboarding({
    hasPermission: can("FINANCIAL_CATEGORY_VIEW", "FINANCIAL_CATEGORY_MANAGE"),
    bankAccounts: ready(ACTIVE_ACCOUNT),
    categories: ready([]),
  })
  assert.deepEqual(manager.categories, { kind: "empty", action: { type: "apply-standard-plan" } })
  assert.equal(manager.ready, false)

  const viewer = financialOnboarding({
    hasPermission: can("FINANCIAL_CATEGORY_VIEW"),
    bankAccounts: ready(ACTIVE_ACCOUNT),
    categories: ready([]),
  })
  assert.deepEqual(viewer.categories, { kind: "empty-no-permission" })
  assert.equal(Object.hasOwn(viewer.categories, "action"), false)
})

test("groups without subcategories do not offer the plan (backend would answer PLAN_NOT_EMPTY)", () => {
  const state = financialOnboarding({
    hasPermission: can("FINANCIAL_CATEGORY_MANAGE"),
    bankAccounts: ready(ACTIVE_ACCOUNT),
    categories: ready([{ kind: "GROUP" }]),
  })
  assert.deepEqual(state.categories, { kind: "incomplete" })
})

test("no active account navigates to /bank-accounts only with BANK_ACCOUNT_MANAGE", () => {
  const manager = financialOnboarding({
    hasPermission: can("BANK_ACCOUNT_VIEW", "BANK_ACCOUNT_MANAGE"),
    bankAccounts: ready([{ active: false }]),
    categories: ready(PLAN),
  })
  assert.deepEqual(manager.bankAccount, { kind: "missing", action: { type: "navigate", to: "/bank-accounts" } })

  const viewer = financialOnboarding({
    hasPermission: can("BANK_ACCOUNT_VIEW"),
    bankAccounts: ready([]),
    categories: ready(PLAN),
  })
  assert.deepEqual(viewer.bankAccount, { kind: "missing-no-permission" })
  assert.equal(Object.hasOwn(viewer.bankAccount, "action"), false)
})

test("active account and a subcategory restore the normal screen", () => {
  const state = financialOnboarding({
    hasPermission: can(),
    bankAccounts: ready(ACTIVE_ACCOUNT),
    categories: ready(PLAN),
  })
  assert.deepEqual(state, { ready: true, bankAccount: { kind: "ok" }, categories: { kind: "ok" } })
})

test("loading and denied catalogs never produce write actions", () => {
  const state = financialOnboarding({
    hasPermission: can("BANK_ACCOUNT_MANAGE", "FINANCIAL_CATEGORY_MANAGE"),
    bankAccounts: { status: "error" },
    categories: { status: "loading" },
  })
  assert.deepEqual(state.bankAccount, { kind: "no-access" })
  assert.deepEqual(state.categories, { kind: "loading" })
  assert.equal(state.ready, false)
})

test("decisions use explicit permissions only, never the CLIENT role", () => {
  // O papel nem é parâmetro: um CLIENT sem permissões explícitas não recebe ações.
  const state = financialOnboarding({
    hasPermission: () => false,
    bankAccounts: ready([]),
    categories: ready([]),
  })
  assert.deepEqual(state.bankAccount, { kind: "missing-no-permission" })
  assert.deepEqual(state.categories, { kind: "empty-no-permission" })
})

test("success and conflicts refresh only the active Company category catalog", async () => {
  const invalidated = []
  const queryClient = { invalidateQueries: async (filters) => { invalidated.push(filters.queryKey) } }

  await refreshFinancialCategories(queryClient, COMPANY_A)

  assert.deepEqual(invalidated, [["companies", COMPANY_A, "financial-categories"]])
  // A chave é prefixo da usada pelo catálogo da tela (…, "financial-categories", "all").
  assert.deepEqual(["companies", COMPANY_A, "financial-categories", "all"].slice(0, 3), invalidated[0])
})

test("PLAN_NOT_EMPTY refreshes the catalog and blocks any further attempt", () => {
  const failure = describeStandardPlanFailure(new ApiError(409, "x", "PLAN_NOT_EMPTY"))
  assert.equal(failure.refreshCatalog, true)
  assert.equal(failure.allowRetry, false)
  assert.doesNotMatch(failure.message, /PLAN_NOT_EMPTY/)

  const concurrent = describeStandardPlanFailure(new ApiError(409, "x", "FINANCIAL_CATEGORY_CONFLICT"))
  assert.equal(concurrent.refreshCatalog, true)
  assert.equal(concurrent.allowRetry, false)
})

test("other standard plan errors map to clear messages", () => {
  assert.match(describeStandardPlanFailure(new ApiError(400, "x", "INVALID_FINANCIAL_CATEGORY")).message, /recusou/)
  const session = describeStandardPlanFailure(new ApiError(401, "x"))
  assert.equal(session.sessionExpired, true)
  assert.match(describeStandardPlanFailure(new ApiError(403, "x")).message, /permissão/)
  assert.equal(describeStandardPlanFailure(new ApiError(403, "x")).allowRetry, false)
  assert.match(describeStandardPlanFailure(new ApiError(404, "x")).message, /não está disponível/)
  const offline = describeStandardPlanFailure(new TypeError("offline"))
  assert.equal(offline.refreshCatalog, true)
  assert.equal(offline.allowRetry, true)
})

test("an onboarding notice never shows in another Company", () => {
  const notice = { companyId: COMPANY_A, tone: "ok", message: "Plano aplicado." }
  assert.equal(noticeForCompany(notice, COMPANY_A), notice)
  assert.equal(noticeForCompany(notice, COMPANY_B), null)
  assert.equal(noticeForCompany(notice, undefined), null)
})
