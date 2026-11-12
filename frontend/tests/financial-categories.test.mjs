import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import {
  applyStandardFinancialCategoryPlan,
  createFinancialCategoryGroup,
  createFinancialSubcategory,
  deleteFinancialCategory,
  getFinancialCategory,
  listFinancialCategories,
  renameFinancialCategory,
  updateFinancialCategoryClassification,
} from "../src/features/financial-categories/api/financial-categories-api.ts"
import {
  FINANCIAL_CATEGORY_READ_PERMISSIONS,
  emptyCatalogActions,
  financialCategoryCapabilities,
  rowActions,
} from "../src/features/financial-categories/lib/financial-category-access.ts"
import {
  classificationFormFrom,
  defaultFinancialCategoryFilter,
  financialCategoryListQuery,
  inheritedFields,
  isUnfilteredEmptyCatalog,
  toClassificationRequest,
  validateClassificationForm,
} from "../src/features/financial-categories/lib/financial-category-form.ts"
import {
  describeFinancialCategoryError,
  handleFinancialCategoryFailure,
} from "../src/features/financial-categories/lib/financial-category-errors.ts"
import { applyStandardFinancialCategoryPlan as onboardingApplyPlan } from "../src/features/financial-entries/api/financial-catalogs-api.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const COMPANY = "c0000000-0000-4000-8000-000000000001"
const GROUP = "g0000000-0000-4000-8000-000000000001"
const SUB = "s0000000-0000-4000-8000-000000000001"
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const can = (...permissions) => (permission) => permissions.includes(permission)
const BASE = `/api/companies/${COMPANY}/financial-categories`

function group(overrides = {}) {
  return {
    id: GROUP, companyId: COMPANY, parentId: null, kind: "GROUP", direction: "EXPENSE",
    code: "2.1", name: "Despesas fictícias", cashActivity: "OPERATING", dreLine: "DADM", costBehavior: null,
    effective: { cashActivity: "OPERATING", dreLine: "DADM", costBehavior: "FIXED" }, version: 3, ...overrides,
  }
}

function sub(overrides = {}) {
  return {
    id: SUB, companyId: COMPANY, parentId: GROUP, kind: "SUBCATEGORY", direction: "EXPENSE",
    code: "2.1.1", name: "Aluguel fictício", cashActivity: null, dreLine: "DCOM", costBehavior: null,
    effective: { cashActivity: "OPERATING", dreLine: "DCOM", costBehavior: "FIXED" }, version: 7, ...overrides,
  }
}

async function capture(call, response = () => new Response(null, { status: 204 })) {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") return Response.json({ headerName: "X-XSRF-TOKEN", token: "csrf-synthetic" })
    return response(path, options)
  }
  const result = await call()
  const commands = calls.filter((entry) => entry.path !== "/api/auth/csrf")
  return { result, command: commands[0], commands }
}
const body = (command) => (command.options.body === undefined ? undefined : JSON.parse(command.options.body))
const csrf = (command) => new Headers(command.options.headers).get("X-XSRF-TOKEN")

// 1–4: visibilidade por permissão.
test("VIEW can open and list but gets no write action", () => {
  const view = financialCategoryCapabilities(can("FINANCIAL_CATEGORY_VIEW"))
  assert.deepEqual(view, { canRead: true, canManageStructure: false, canClassify: false })
  assert.deepEqual(rowActions(group(), view), [])
  assert.deepEqual(rowActions(sub(), view), [])
  assert.deepEqual(emptyCatalogActions(view), [])
})

test("STRUCTURE_MANAGE gets plan/create/rename/delete but never classification", () => {
  const structure = financialCategoryCapabilities(can("FINANCIAL_CATEGORY_STRUCTURE_MANAGE"))
  assert.deepEqual(structure, { canRead: true, canManageStructure: true, canClassify: false })
  assert.deepEqual(rowActions(group(), structure), ["create-subcategory", "rename", "delete"])
  assert.deepEqual(rowActions(sub(), structure), ["rename", "delete"])
  assert.deepEqual(emptyCatalogActions(structure), ["apply-standard-plan", "create-first-group"])
  for (const category of [group(), sub()]) assert.equal(rowActions(category, structure).includes("classify"), false)
})

test("MANAGE gets structure plus classification", () => {
  const manage = financialCategoryCapabilities(can("FINANCIAL_CATEGORY_MANAGE"))
  assert.deepEqual(manage, { canRead: true, canManageStructure: true, canClassify: true })
  assert.deepEqual(rowActions(group(), manage), ["create-subcategory", "rename", "classify", "delete"])
  assert.deepEqual(rowActions(sub(), manage), ["rename", "classify", "delete"])
})

test("no category permission (or only unrelated ones) reads nothing; roles never count", () => {
  for (const permissions of [[], ["FINANCIAL_ENTRY_MANAGE", "BANK_ACCOUNT_MANAGE", "RESULT_CENTER_MANAGE"]]) {
    assert.deepEqual(financialCategoryCapabilities(can(...permissions)), { canRead: false, canManageStructure: false, canClassify: false })
  }
  assert.deepEqual([...FINANCIAL_CATEGORY_READ_PERMISSIONS].sort(),
    ["FINANCIAL_CATEGORY_MANAGE", "FINANCIAL_CATEGORY_STRUCTURE_MANAGE", "FINANCIAL_CATEGORY_VIEW"])
})

// 5–6: comandos, versão, CSRF.
test("rename uses the dedicated POST /rename with only name + expectedVersion, never PUT", async () => {
  const { command, commands } = await capture(() => renameFinancialCategory(COMPANY, sub(), "  Novo nome  "),
    () => Response.json(sub({ name: "Novo nome", version: 8 })))
  assert.equal(commands.length, 1)
  assert.equal(command.path, `${BASE}/${SUB}/rename`)
  assert.equal(command.options.method, "POST")
  assert.equal(csrf(command), "csrf-synthetic")
  assert.deepEqual(body(command), { name: "Novo nome", expectedVersion: 7 })
})

test("classification PUT, rename and delete all send the version received from the API", async () => {
  const update = await capture(() => updateFinancialCategoryClassification(COMPANY, sub(), { cashActivity: "INVESTING", dreLine: null, costBehavior: "VARIABLE" }),
    () => Response.json(sub({ version: 8 })))
  assert.equal(update.command.path, `${BASE}/${SUB}`)
  assert.equal(update.command.options.method, "PUT")
  assert.equal(csrf(update.command), "csrf-synthetic")
  // O PUT exige o nome; vai o nome atual, sem edição (renomear é outro comando).
  assert.deepEqual(body(update.command), { name: "Aluguel fictício", cashActivity: "INVESTING", dreLine: null, costBehavior: "VARIABLE", expectedVersion: 7 })

  const removal = await capture(() => deleteFinancialCategory(COMPANY, group()))
  assert.equal(removal.command.path, `${BASE}/${GROUP}?version=3`)
  assert.equal(removal.command.options.method, "DELETE")
  assert.equal(csrf(removal.command), "csrf-synthetic")

  const zero = await capture(() => deleteFinancialCategory(COMPANY, group({ version: 0 })))
  assert.equal(zero.command.path, `${BASE}/${GROUP}?version=0`)
})

test("create group/subcategory and standard plan send no code, sequence or classification", async () => {
  const created = await capture(() => createFinancialCategoryGroup(COMPANY, { direction: "INCOME", name: " Receitas fictícias " }),
    () => Response.json(group({ direction: "INCOME", code: "1.1" }), { status: 201 }))
  assert.equal(created.command.path, `${BASE}/groups`)
  assert.equal(created.command.options.method, "POST")
  assert.deepEqual(body(created.command), { direction: "INCOME", name: "Receitas fictícias" })

  const child = await capture(() => createFinancialSubcategory(COMPANY, group(), "Sub fictícia"),
    () => Response.json(sub(), { status: 201 }))
  assert.equal(child.command.path, `${BASE}/${GROUP}/subcategories`)
  assert.deepEqual(body(child.command), { name: "Sub fictícia" })

  const plan = await capture(() => applyStandardFinancialCategoryPlan(COMPANY),
    () => Response.json({ version: "FINEMP_WEB_1", groups: 11, subcategories: 60 }, { status: 201 }))
  assert.equal(plan.command.path, `${BASE}/standard-plan`)
  assert.equal(plan.command.options.method, "POST")
  assert.equal(plan.command.options.body, undefined)
  assert.equal(csrf(plan.command), "csrf-synthetic")

  for (const sent of [body(created.command), body(child.command)]) {
    for (const forbidden of ["code", "sequenceNumber", "sortKey", "cashActivity", "dreLine", "costBehavior", "expectedVersion"]) {
      assert.equal(Object.hasOwn(sent, forbidden), false, forbidden)
    }
  }
  // O onboarding (#317) usa o mesmo comando, não um segundo caminho HTTP.
  assert.equal(onboardingApplyPlan, applyStandardFinancialCategoryPlan)
})

test("get reads a single category scoped to the Company", async () => {
  const { command } = await capture(() => getFinancialCategory(COMPANY, SUB), () => Response.json(sub()))
  assert.equal(command.path, `${BASE}/${SUB}`)
  assert.equal(command.options.method ?? "GET", "GET")
})

// 7: raw para editar, effective só para exibir.
test("classification form starts from raw values; effective is presentation only", () => {
  const form = classificationFormFrom(sub())
  assert.deepEqual(form, { cashActivity: "", dreLine: "DCOM", costBehavior: "" })
  assert.deepEqual(toClassificationRequest(form), { cashActivity: null, dreLine: "DCOM", costBehavior: null })
  assert.deepEqual(inheritedFields(sub()), ["cashActivity", "costBehavior"])
  assert.deepEqual(inheritedFields(group({ costBehavior: null })), [])
  assert.deepEqual(inheritedFields(sub({ cashActivity: "FINANCING", dreLine: "NAO", costBehavior: "VARIABLE" })), [])

  // Grupo: atividade e DRE obrigatórias (regra do PUT); custo pode ficar no padrão.
  assert.deepEqual(validateClassificationForm(group(), { cashActivity: "", dreLine: "", costBehavior: "" }),
    { cashActivity: "Escolha a atividade do grupo.", dreLine: "Escolha a linha da DRE do grupo." })
  assert.deepEqual(validateClassificationForm(sub(), { cashActivity: "", dreLine: "", costBehavior: "" }), {})
})

// 8–9: estado vazio sem aplicação automática.
test("empty state is only for the unfiltered first page and never applies the plan by itself", () => {
  const page = (totalItems) => ({ items: [], page: 0, size: 50, totalItems, totalPages: 0 })
  assert.equal(isUnfilteredEmptyCatalog(defaultFinancialCategoryFilter, 0, page(0)), true)
  assert.equal(isUnfilteredEmptyCatalog({ ...defaultFinancialCategoryFilter, search: "x" }, 0, page(0)), false)
  assert.equal(isUnfilteredEmptyCatalog({ ...defaultFinancialCategoryFilter, direction: "INCOME" }, 0, page(0)), false)
  assert.equal(isUnfilteredEmptyCatalog(defaultFinancialCategoryFilter, 0, undefined), false)

})

// 10: filtros e paginação no servidor.
test("search, direction, kind and page go to the server", async () => {
  assert.deepEqual(Object.fromEntries(financialCategoryListQuery(defaultFinancialCategoryFilter, 0)), { page: "0", size: "50" })
  assert.deepEqual(Object.fromEntries(financialCategoryListQuery({ search: "  aluguel ", direction: "EXPENSE", kind: "SUBCATEGORY" }, 2)),
    { page: "2", size: "50", search: "aluguel", direction: "EXPENSE", kind: "SUBCATEGORY" })

  const { command } = await capture(() => listFinancialCategories(COMPANY, { search: "luz", direction: "INCOME", kind: "GROUP" }, 1),
    () => Response.json({ items: [], page: 1, size: 50, totalItems: 0, totalPages: 0 }))
  const url = new URL(command.path, "https://finemp.example.invalid")
  assert.equal(url.pathname, BASE)
  assert.deepEqual(Object.fromEntries(url.searchParams), { page: "1", size: "50", search: "luz", direction: "INCOME", kind: "GROUP" })
})

// 11–12: erros.
test("errors map to clear messages without codes or bodies", () => {
  const message = (status, code) => describeFinancialCategoryError(new ApiError(status, "x", code)).message
  assert.match(message(409, "CATEGORY_IN_USE"), /lançamentos vinculados/)
  assert.match(message(409, "PLAN_NOT_EMPTY"), /já tem categorias/)
  assert.match(message(409, "NOT_A_GROUP"), /dentro de um grupo/)
  assert.match(message(409, "STALE_VERSION"), /outra sessão/)
  assert.match(message(400, "INVALID_FINANCIAL_CATEGORY"), /Confira/)
  assert.match(message(403), /permissão/)
  assert.match(message(404), /não encontrada/)
  for (const [status, code] of [[409, "CATEGORY_IN_USE"], [409, "STALE_VERSION"], [500, "SQL_BOOM"], [503]]) {
    assert.doesNotMatch(message(status, code), /CATEGORY_IN_USE|STALE_VERSION|SQL|\d{3}/)
  }
  assert.doesNotMatch(describeFinancialCategoryError(new TypeError("fetch failed at line 1")).message, /fetch failed|line/)
  // Excluir em uso não oferece forçar nem fecha sozinho com ação alternativa.
  assert.equal(describeFinancialCategoryError(new ApiError(409, "x", "CATEGORY_IN_USE")).refetch, false)
})

test("STALE_VERSION refetches the Company catalog and closes the dialog instead of overwriting", async () => {
  const stale = describeFinancialCategoryError(new ApiError(409, "x", "STALE_VERSION"))
  assert.equal(stale.refetch, true)
  assert.equal(stale.closeDialog, true)

  const invalidated = []
  const queryClient = { invalidateQueries: async ({ queryKey }) => { invalidated.push(queryKey) }, setQueryData: () => {} }
  const result = await handleFinancialCategoryFailure(queryClient, COMPANY, new ApiError(409, "x", "STALE_VERSION"))
  assert.equal(result.closeDialog, true)
  assert.deepEqual(invalidated, [["companies", COMPANY, "financial-categories"]])

  invalidated.length = 0
  await handleFinancialCategoryFailure(queryClient, COMPANY, new ApiError(400, "x", "INVALID_FINANCIAL_CATEGORY"))
  assert.deepEqual(invalidated, [])
})

test("401 ends the session instead of showing a category message", async () => {
  const set = []
  const queryClient = { invalidateQueries: async () => {}, setQueryData: (key, value) => set.push([key, value]) }
  const result = await handleFinancialCategoryFailure(queryClient, COMPANY, new ApiError(401, "x"))
  assert.equal(result.sessionExpired, true)
  assert.deepEqual(set, [[["current-user"], null]])
})
