import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  listCompanyAuditEvents,
  listGlobalAuditEvents,
} from "../src/features/audit/api/audit-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("audit clients keep global and Company scopes distinct", async () => {
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

  await listGlobalAuditEvents(0, 50)
  await listCompanyAuditEvents("company-1", 1, 50)

  assert.equal(calls[0].path, "/api/admin/audit-events?page=0&size=50")
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/audit-events?page=1&size=50",
  )
})

test("audit routes are real and guarded by SYSTEM_ADMIN", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )

  assert.match(router, /path="\/admin\/activity-log"[\s\S]*?permission="SYSTEM_ADMIN"/)
  assert.match(router, /path="\/audit"[\s\S]*?permission="SYSTEM_ADMIN"/)
  assert.match(navigation, /id: "admLog"[\s\S]*?path: "\/admin\/activity-log"/)
  assert.match(navigation, /id: "audit"[\s\S]*?path: "\/audit"/)
})


test("Company audit resets local pagination when the selected Company changes", () => {
  const page = readFileSync(
    new URL("../src/features/audit/pages/audit-pages.tsx", import.meta.url),
    "utf8",
  )

  assert.match(
    page,
    /<AuditLogView[\s\S]*?key=\{selectedCompany\.id\}[\s\S]*?companyId=\{selectedCompany\.id\}/,
  )
})
