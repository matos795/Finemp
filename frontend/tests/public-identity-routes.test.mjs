import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  IDENTITY_PATHS,
  PUBLIC_ROUTE_PATHS,
} from "../src/features/auth/lib/identity-flow.ts"

// Sem stack de renderização: estas verificações leem a fonte para garantir
// a estrutura de rotas e o que as páginas públicas nunca usam com o token.
test("login offers 'Esqueci minha senha' pointing to /forgot-password", () => {
  const login = readFileSync(new URL("../src/features/auth/pages/login-page.tsx", import.meta.url), "utf8")

  assert.equal(IDENTITY_PATHS.forgotPassword, "/forgot-password")
  assert.match(login, /Esqueci minha senha/)
  assert.match(login, /to=\{IDENTITY_PATHS\.forgotPassword\}/)
  assert.doesNotMatch(login, /location\.search|URLSearchParams|useSearchParams/)
})

test("the identity pages are public routes declared outside ProtectedApp", () => {
  const router = readFileSync(new URL("../src/app/router/app-router.tsx", import.meta.url), "utf8")

  assert.deepEqual([...PUBLIC_ROUTE_PATHS].sort(), ["/activate", "/forgot-password", "/login", "/reset-password"])
  const protectedAt = router.indexOf("<Route element={<ProtectedApp />}>")
  assert.ok(protectedAt > 0)
  for (const name of ["login", "activate", "forgotPassword", "resetPassword"]) {
    const at = router.indexOf(`path={IDENTITY_PATHS.${name}}`)
    assert.ok(at > 0 && at < protectedAt, name)
  }
  for (const page of ["<ActivatePage />", "<ForgotPasswordPage />", "<ResetPasswordPage />"]) {
    const at = router.indexOf(page)
    assert.ok(at > 0 && at < protectedAt, page)
  }
})

test("identity pages keep the token out of query keys, storage, logs and toasts", () => {
  for (const file of ["activate-page.tsx", "reset-password-page.tsx", "forgot-password-page.tsx"]) {
    const source = readFileSync(new URL(`../src/features/auth/pages/${file}`, import.meta.url), "utf8")
    assert.doesNotMatch(source, /localStorage|sessionStorage|console\.|useQuery\(|queryKey|useToast|showToast|URLSearchParams|useSearchParams|\bfetch\(/, file)
  }
})
