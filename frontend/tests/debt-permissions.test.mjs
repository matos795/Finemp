import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("debt permissions exist in both company access editors", () => {
  const onboarding = readFileSync(
    new URL("../src/features/platform-admin/components/official-user-onboarding-section.tsx", import.meta.url),
    "utf8",
  )
  const selfAccess = readFileSync(
    new URL("../src/features/platform-admin/pages/platform-admin-page.tsx", import.meta.url),
    "utf8",
  )
  const authTypes = readFileSync(
    new URL("../src/features/auth/types/auth.types.ts", import.meta.url),
    "utf8",
  )

  for (const source of [onboarding, selfAccess, authTypes]) {
    assert.match(source, /DEBT_VIEW/)
    assert.match(source, /DEBT_MANAGE/)
  }
})
