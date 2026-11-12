import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { getCompanyProfile } from "../src/features/companies/api/company-profile-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("Company profile uses the selected Company endpoint", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return Response.json({
      id: "company-1",
      legalName: "Empresa Exemplo",
      cnpj: "00000000000000",
    })
  }

  const result = await getCompanyProfile("company-1")

  assert.equal(result.id, "company-1")
  assert.equal(calls[0].path, "/api/companies/company-1/profile")
  assert.equal(calls[0].options.credentials, "include")
})

test("Dados da empresa is a real read-only route", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/companies/pages/company-details-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(router, /path="\/company-details"/)
  assert.match(navigation, /id: "cadEmp"[\s\S]*?path: "\/company-details"/)
  assert.match(page, /somente leitura/)
  assert.doesNotMatch(page, /method:\s*"PUT"|method:\s*"POST"|Salvar/)
})
