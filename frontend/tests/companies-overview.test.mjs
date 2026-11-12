import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("Companies overview uses only the current authorization projection", () => {
  const page = readFileSync(
    new URL("../src/features/companies/pages/companies-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(page, /user\.companies\.map/)
  assert.match(page, /selectCompany/)
  assert.match(page, /Nenhuma empresa está disponível/)
  assert.doesNotMatch(
    page,
    /createCompany|replaceCompanyAccess|grant|POST|PUT|DELETE/,
  )
})

test("Companies route is protected only by authentication shell", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  const at = router.indexOf('path="/companies"')
  assert.ok(at > router.indexOf("<Route element={<ProtectedApp />}>"))

  const route = router.slice(at, at + 220)
  assert.match(route, /<CompaniesPage \/>/)
  assert.doesNotMatch(route, /RequirePermission|RequirePlatformPermission/)
})
