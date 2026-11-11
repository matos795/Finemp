import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import {
  businessPartnerQuery,
  createBusinessPartner,
  deleteBusinessPartner,
  updateBusinessPartner,
} from "../src/features/business-partners/api/business-partners-api.ts"
import {
  businessPartnerFormFrom,
  formatPartnerDocument,
  newBusinessPartnerForm,
  toBusinessPartnerProfile,
  validateBusinessPartnerForm,
} from "../src/features/business-partners/lib/business-partner-form.ts"
import { describeBusinessPartnerError } from "../src/features/business-partners/lib/business-partner-errors.ts"
import { canManageBusinessPartners } from "../src/features/business-partners/lib/business-partner-access.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

const COMPANY = "c0000000-0000-4000-8000-000000000001"
const can = (...permissions) => (permission) => permissions.includes(permission)

function captureWrites(response) {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" })
    }
    return Response.json(response, { status: options.method === "POST" ? 201 : 200 })
  }
  return () => calls.filter((call) => call.path !== "/api/auth/csrf")
}

function filledForm() {
  return {
    ...newBusinessPartnerForm(),
    document: "11.222.333/0001-81",
    name: " Fornecedor Fictício Ltda ",
    tradeName: "Fictício",
    role: "SUPPLIER",
    email: "",
    city: "Cidade Fictícia",
    stateCode: "sp",
  }
}

test("list query keeps role, active and search on the server", () => {
  assert.deepEqual(
    Object.fromEntries(businessPartnerQuery({ role: "", active: "true", search: "" }, 0)),
    { page: "0", size: "50", active: "true" },
  )
  assert.deepEqual(
    Object.fromEntries(businessPartnerQuery({ role: "BOTH", active: "false", search: "  fict " }, 2)),
    { page: "2", size: "50", role: "BOTH", active: "false", search: "fict" },
  )
})

test("create posts the profile without expectedVersion; update and delete carry the version", async () => {
  const writes = captureWrites({ id: "p1", version: 0, profile: {} })
  const profile = toBusinessPartnerProfile(filledForm())

  await createBusinessPartner(COMPANY, profile)
  await updateBusinessPartner(COMPANY, "p1", profile, 3)
  await deleteBusinessPartner(COMPANY, "p1", 4)

  const [create, update, remove] = writes()
  assert.equal(create.path, `/api/companies/${COMPANY}/business-partners`)
  assert.equal(create.options.method, "POST")
  assert.deepEqual(Object.keys(JSON.parse(create.options.body)), ["profile"])

  assert.equal(update.path, `/api/companies/${COMPANY}/business-partners/p1`)
  assert.equal(update.options.method, "PUT")
  assert.equal(JSON.parse(update.options.body).expectedVersion, 3)

  assert.equal(remove.path, `/api/companies/${COMPANY}/business-partners/p1?version=4`)
  assert.equal(remove.options.method, "DELETE")
  assert.equal(new Headers(remove.options.headers).get("X-XSRF-TOKEN"), "t")
})

test("profile sends trimmed texts, empty optionals as null and no trade name for individuals", () => {
  const profile = toBusinessPartnerProfile(filledForm())
  assert.equal(profile.personType, "LEGAL_ENTITY")
  assert.equal(profile.document, "11.222.333/0001-81", "document normalization belongs to the backend")
  assert.equal(profile.name, "Fornecedor Fictício Ltda")
  assert.equal(profile.email, null)
  assert.equal(profile.stateCode, "sp", "UF normalization belongs to the backend")
  assert.equal(profile.active, true)

  const individual = toBusinessPartnerProfile({ ...filledForm(), personType: "INDIVIDUAL", tradeName: "ignorado" })
  assert.equal(individual.tradeName, null)
})

test("UX validation only checks required fields; CPF/CNPJ digits stay with the backend", () => {
  assert.deepEqual(validateBusinessPartnerForm(filledForm()), {})
  const errors = validateBusinessPartnerForm({ ...newBusinessPartnerForm(), name: "A" })
  assert.deepEqual(Object.keys(errors).sort(), ["document", "name", "role"])
  // Dígito verificador inválido não é barrado no React: o backend responde 400.
  assert.deepEqual(validateBusinessPartnerForm({ ...filledForm(), document: "11.111.111/1111-11" }), {})
})

test("edit form starts from the stored profile", () => {
  const form = businessPartnerFormFrom({
    id: "p1", version: 2,
    profile: { personType: "INDIVIDUAL", document: "12345678909", name: "Pessoa Fictícia", tradeName: "", role: "CUSTOMER", active: false, phone: "", email: "x@exemplo.invalid", contactName: "", city: "", stateCode: "", postalCode: "", street: "", streetNumber: "", complement: "", district: "", municipalityCode: "", stateRegistration: "", municipalRegistration: "", notes: "" },
  })
  assert.equal(form.personType, "INDIVIDUAL")
  assert.equal(form.role, "CUSTOMER")
  assert.equal(form.active, false)
  assert.equal(form.email, "x@exemplo.invalid")
})

test("documents are only formatted for display", () => {
  assert.equal(formatPartnerDocument("12345678909"), "123.456.789-09")
  assert.equal(formatPartnerDocument("11222333000181"), "11.222.333/0001-81")
  assert.equal(formatPartnerDocument("12ABC34501DE35"), "12.ABC.345/01DE-35")
  assert.equal(formatPartnerDocument("123"), "123")
})

test("write actions require BUSINESS_PARTNER_MANAGE, never a role", () => {
  assert.equal(canManageBusinessPartners(can("BUSINESS_PARTNER_MANAGE")), true)
  assert.equal(canManageBusinessPartners(can("BUSINESS_PARTNER_VIEW", "FINANCIAL_ENTRY_MANAGE")), false)
  assert.equal(canManageBusinessPartners(() => false), false)
})

test("errors map to messages without internal codes or personal data", () => {
  const message = (status, code, operation = "save") => describeBusinessPartnerError(new ApiError(status, "x", code), operation)
  assert.match(message(409, "DUPLICATE_DOCUMENT"), /já existe.*documento/i)
  assert.match(message(409, "STALE_VERSION"), /recarregue/i)
  assert.match(message(409, "BUSINESS_PARTNER_CONFLICT"), /recarregue/i)
  assert.match(message(400, "INVALID_BUSINESS_PARTNER"), /CPF\/CNPJ/)
  assert.match(message(403), /permissão/)
  assert.match(message(404), /não encontrado/)
  assert.match(message(404, undefined, "list"), /não estão disponíveis/)
  assert.match(message(401), /sessão/)
  for (const status of [400, 403, 404, 409]) {
    assert.doesNotMatch(message(status, "SOME_CODE"), /SOME_CODE/)
  }
})
