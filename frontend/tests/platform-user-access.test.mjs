import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  canEditPlatformPermission,
  isSystemAdmin,
} from "../src/features/platform-admin/lib/platform-user-access.ts"

test("only SYSTEM_ADMIN can edit the SYSTEM_ADMIN capability", () => {
  assert.equal(
    canEditPlatformPermission(
      ["PLATFORM_USER_MANAGE"],
      [],
      "SYSTEM_ADMIN",
    ),
    false,
  )

  assert.equal(
    canEditPlatformPermission(
      ["SYSTEM_ADMIN"],
      [],
      "SYSTEM_ADMIN",
    ),
    true,
  )
})

test("a normal platform administrator cannot modify a SYSTEM_ADMIN target", () => {
  assert.equal(
    canEditPlatformPermission(
      ["PLATFORM_USER_MANAGE"],
      ["SYSTEM_ADMIN", "PLATFORM_USER_MANAGE"],
      "ACCOUNTING_FIRM_VIEW",
    ),
    false,
  )

  assert.equal(
    canEditPlatformPermission(
      ["SYSTEM_ADMIN"],
      ["SYSTEM_ADMIN"],
      "ACCOUNTING_FIRM_VIEW",
    ),
    true,
  )
})

test("SYSTEM_ADMIN identification is explicit", () => {
  assert.equal(isSystemAdmin(["SYSTEM_ADMIN"]), true)
  assert.equal(isSystemAdmin(["PLATFORM_USER_MANAGE"]), false)
})

test("Usuários e acessos is a published guarded route", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )

  assert.match(router, /path="\/admin\/users"/)
  assert.match(router, /<PlatformUsersPage \/>/)
  assert.match(router, /permission="PLATFORM_USER_MANAGE"/)

  assert.match(
    navigation,
    /id: "admUsers"[\s\S]*?path: "\/admin\/users"[\s\S]*?PLATFORM_USER_MANAGE/,
  )
})

test("user access page keeps status read-only and delegates permission changes to the official editor", () => {
  const page = readFileSync(
    new URL("../src/features/platform-admin/pages/platform-users-page.tsx", import.meta.url),
    "utf8",
  )

  assert.match(page, /PlatformPermissionsEditor/)
  assert.match(page, /Convite pendente|Conta ativa|Conta inativa/)
  assert.doesNotMatch(page, /setUserActive|\/status/)
})
