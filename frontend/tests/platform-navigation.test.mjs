import { test } from "node:test"
import assert from "node:assert/strict"
import {
  isItemVisible,
  navigationModules,
} from "../src/components/layout/navigation.ts"

const adminItem = navigationModules
  .find((module) => module.id === "admin")
  .items.find((item) => item.id === "adminOnboarding")

const dashboardItem = navigationModules
  .find((module) => module.id === "fluxo")
  .items.find((item) => item.id === "painel")

test("platform admin can see onboarding without a Company grant", () => {
  assert.equal(
    isItemVisible(
      adminItem,
      () => false,
      (permission) => permission === "PLATFORM_USER_MANAGE",
    ),
    true,
  )
})

test("Company navigation remains unavailable without Company permission", () => {
  assert.equal(
    isItemVisible(
      dashboardItem,
      () => false,
      () => true,
    ),
    false,
  )
})

test("non-platform user cannot see platform onboarding", () => {
  assert.equal(
    isItemVisible(
      adminItem,
      () => true,
      () => false,
    ),
    false,
  )
})
