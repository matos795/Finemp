import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("accounts and balances renders the backend-derived current balance only", () => {
  const page = readFileSync(
    new URL("../src/features/reports/pages/account-balances-page.tsx", import.meta.url),
    "utf8",
  )
  const api = readFileSync(
    new URL("../src/features/reports/api/reports-api.ts", import.meta.url),
    "utf8",
  )
  const hook = readFileSync(
    new URL("../src/features/reports/hooks/use-account-balances.ts", import.meta.url),
    "utf8",
  )
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )

  assert.match(page, /account\.currentBalance/)
  assert.match(page, /account\.openingBalance/)
  assert.doesNotMatch(page, /\.reduce\(|nominalAmount|actualPaidAmount|transfer/i)

  assert.match(api, /financial-reports.*account-balances/s)
  assert.match(hook, /getAccountBalances/)
  assert.match(router, /path="\/cash-flow\/accounts"[\s\S]*?RequirePermissionGroups/)
  assert.match(router, /BANK_ACCOUNT_VIEW[\s\S]*?FINANCIAL_ENTRY_VIEW/)
})
