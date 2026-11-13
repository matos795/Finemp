import test from "node:test"
import assert from "node:assert/strict"
import { canUseCompanyPermission } from "../src/features/companies/company-permission-access.ts"

const company = {
  id: "company-1",
  name: "Synthetic Company",
  role: "ADMIN",
  permissions: ["FINANCIAL_ENTRY_VIEW"],
  costCenterScope: "ALL",
}

test("SYSTEM_ADMIN can use company permissions missing from the serialized grant", () => {
  const user = {
    id: "user-1",
    name: "Synthetic Admin",
    email: "admin@example.invalid",
    platformPermissions: ["SYSTEM_ADMIN"],
    companies: [company],
  }

  assert.equal(
    canUseCompanyPermission(user, company, "CRM_VIEW"),
    true,
  )
})


test("SYSTEM_ADMIN without an active company cannot use company-scoped actions", () => {
  const user = {
    id: "user-1",
    name: "Synthetic Admin",
    email: "admin@example.invalid",
    platformPermissions: ["SYSTEM_ADMIN"],
    companies: [],
  }

  assert.equal(
    canUseCompanyPermission(user, null, "CRM_MANAGE"),
    false,
  )
})

test("ordinary users still need the explicit company permission", () => {
  const user = {
    id: "user-2",
    name: "Synthetic User",
    email: "user@example.invalid",
    platformPermissions: [],
    companies: [company],
  }

  assert.equal(
    canUseCompanyPermission(user, company, "CRM_VIEW"),
    false,
  )
  assert.equal(
    canUseCompanyPermission(user, company, "FINANCIAL_ENTRY_VIEW"),
    true,
  )
})
