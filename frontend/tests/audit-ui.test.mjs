import { afterEach, test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  listCompanyAuditEvents,
  listPlatformAuditEvents,
} from "../src/features/audit/api/audit-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("audit clients use server-side global and Company scopes", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    return Response.json({
      items: [],
      page: 0,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
  }

  await listPlatformAuditEvents(2, 50)
  await listCompanyAuditEvents("company-1", 1, 25)

  assert.equal(
    calls[0].path,
    "/api/admin/audit-events?page=2&size=50",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/audit-events?page=1&size=25",
  )
})

test("audit UI never renders free-form persisted states", () => {
  const globalPage = readFileSync(
    new URL("../src/features/audit/pages/platform-audit-page.tsx", import.meta.url),
    "utf8",
  )
  const companyPage = readFileSync(
    new URL("../src/features/audit/pages/company-audit-page.tsx", import.meta.url),
    "utf8",
  )

  for (const source of [globalPage, companyPage]) {
    assert.doesNotMatch(source, /beforeState|afterState|permissionSnapshot/)
    assert.doesNotMatch(source, /deleteAudit|updateAudit|POST|PUT|DELETE/)
  }
})


test("audit routes and navigation are SYSTEM_ADMIN-only", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )

  assert.match(router, /path="\/admin\/activity-log"[\s\S]*?permission="SYSTEM_ADMIN"/)
  assert.match(router, /path="\/audit-trail"[\s\S]*?permission="SYSTEM_ADMIN"/)

  assert.match(
    navigation,
    /id: "admLog"[\s\S]*?path: "\/admin\/activity-log"[\s\S]*?SYSTEM_ADMIN/,
  )
  assert.match(
    navigation,
    /id: "audit"[\s\S]*?path: "\/audit-trail"[\s\S]*?SYSTEM_ADMIN/,
  )
})
