import { test, afterEach, beforeEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  activateAccount,
  completePasswordReset,
  requestPasswordReset,
} from "../src/features/auth/api/auth-api.ts"
import {
  readAndClearAccountActionToken,
  releaseCapturedAccountActionToken,
} from "../src/features/auth/lib/account-action-token.ts"
import {
  validateNewPassword,
  utf8ByteLength,
} from "../src/features/auth/lib/account-password-policy.ts"
import {
  FORGOT_PASSWORD_NEUTRAL_MESSAGE,
  IDENTITY_PATHS,
  INVALID_LINK_MESSAGE,
  PUBLIC_ROUTE_PATHS,
  TOO_MANY_ATTEMPTS_MESSAGE,
  accountActionSuccessNavigation,
  describeAccountActionError,
  describePasswordResetRequestError,
  loginNoticeFrom,
} from "../src/features/auth/lib/identity-flow.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

// Token sintético no formato do backend (base64url de 32 bytes = 43 caracteres).
const TOKEN = "Synthetic_token-only-for-tests_0123456789ab"
const originalFetch = globalThis.fetch

function storageSpy() {
  const touched = []
  return {
    touched,
    getItem: (key) => { touched.push(["get", key]); return null },
    setItem: (key, value) => { touched.push(["set", key, value]) },
    removeItem: (key) => { touched.push(["remove", key]) },
    clear: () => { touched.push(["clear"]) },
  }
}

function addressBar(href) {
  const url = new URL(href)
  const replaced = []
  const bar = {
    replaced,
    location: { pathname: url.pathname, search: url.search, hash: url.hash },
    history: {
      state: { idx: 0, key: "synthetic" },
      replaceState(state, unused, next) {
        replaced.push({ state, next })
        const nextUrl = new URL(next, "https://finemp.example.invalid")
        bar.location = { pathname: nextUrl.pathname, search: nextUrl.search, hash: nextUrl.hash }
      },
    },
  }
  return bar
}

beforeEach(() => {
  releaseCapturedAccountActionToken()
  globalThis.localStorage = storageSpy()
  globalThis.sessionStorage = storageSpy()
})

afterEach(() => {
  globalThis.fetch = originalFetch
  delete globalThis.localStorage
  delete globalThis.sessionStorage
})

test("/activate#token=… captures the token and removes the fragment immediately", () => {
  const bar = addressBar(`https://finemp.example.invalid/activate#token=${TOKEN}`)

  assert.equal(readAndClearAccountActionToken(bar), TOKEN)

  assert.equal(bar.replaced.length, 1)
  assert.equal(bar.replaced[0].next, "/activate")
  assert.deepEqual(bar.replaced[0].state, { idx: 0, key: "synthetic" })
  assert.equal(bar.location.hash, "")
  assert.doesNotMatch(bar.location.pathname + bar.location.search, new RegExp(TOKEN))
})

test("the token never becomes a query string nor goes to localStorage/sessionStorage", () => {
  const bar = addressBar(`https://finemp.example.invalid/reset-password?from=mail#token=${TOKEN}`)

  assert.equal(readAndClearAccountActionToken(bar), TOKEN)

  assert.equal(bar.replaced[0].next, "/reset-password?from=mail")
  for (const { next, state } of bar.replaced) {
    assert.doesNotMatch(next, new RegExp(TOKEN))
    assert.doesNotMatch(JSON.stringify(state), new RegExp(TOKEN))
  }
  assert.deepEqual(globalThis.localStorage.touched, [])
  assert.deepEqual(globalThis.sessionStorage.touched, [])
})

test("missing or malformed token returns null (no API call) but still clears the fragment", () => {
  for (const hash of ["", "#", "#token=", "#token=short", `#token=${TOKEN}x`, `#token=${TOKEN.slice(0, 42)}!`, `#other=${TOKEN}`, `#token=${TOKEN}&token=${TOKEN}`]) {
    releaseCapturedAccountActionToken()
    const bar = addressBar(`https://finemp.example.invalid/activate${hash}`)

    assert.equal(readAndClearAccountActionToken(bar), null, hash)
    assert.equal(bar.location.hash, "", hash)
  }
})

test("StrictMode double render on the same page gets the same token until it is released", () => {
  const bar = addressBar(`https://finemp.example.invalid/activate#token=${TOKEN}`)

  assert.equal(readAndClearAccountActionToken(bar), TOKEN)
  assert.equal(readAndClearAccountActionToken(bar), TOKEN)

  // Outra página não herda o token.
  assert.equal(readAndClearAccountActionToken(addressBar("https://finemp.example.invalid/reset-password")), null)

  releaseCapturedAccountActionToken()
  assert.equal(readAndClearAccountActionToken(bar), null)
})

test("password policy mirrors the backend: 8+ characters, an ASCII letter, a digit, at most 72 UTF-8 bytes", () => {
  const ok = (password) => assert.deepEqual(validateNewPassword(password, password), {}, password)
  const fails = (password, field = "password") => assert.ok(validateNewPassword(password, password)[field], password)

  ok("abcdefg1")
  ok("12345678a")
  ok("senha com espaço 1")
  fails("abcdef1")          // 7 caracteres
  fails("")
  fails("abcdefgh")         // sem número
  fails("12345678")         // sem letra
  fails("éééééééé1")        // letra acentuada não conta (backend: [a-zA-Z])
  fails("١٢٣٤٥٦٧٨a")       // dígito não ASCII não conta (backend: \\d ASCII)

  ok("a1" + "x".repeat(70))                 // 72 bytes
  fails("a1" + "x".repeat(71))              // 73 bytes
  ok("a1" + "é".repeat(35))                 // 2 + 70 = 72 bytes
  fails("a1" + "é".repeat(36))              // 38 caracteres, mas 74 bytes
  assert.equal(utf8ByteLength("é"), 2)
  assert.equal(utf8ByteLength("€"), 3)
  assert.equal(utf8ByteLength("😀"), 4)
})

test("password confirmation must match and both fields are required", () => {
  assert.ok(validateNewPassword("abcdefg1", "abcdefg2").confirmation)
  assert.ok(validateNewPassword("abcdefg1", "").confirmation)
  assert.deepEqual(validateNewPassword("abcdefg1", "abcdefg1"), {})
  for (const message of Object.values(validateNewPassword("abcdefg1", "abcdefg2"))) {
    assert.doesNotMatch(message, /abcdefg/)
  }
})

async function captureCommand(call, status = 204) {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") {
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "csrf-synthetic" })
    }
    return new Response(null, { status })
  }
  await call()
  const command = calls.find((entry) => entry.path !== "/api/auth/csrf")
  return { command, calls }
}

test("activate posts { token, newPassword } to /api/auth/activate with CSRF and cookies", async () => {
  const { command } = await captureCommand(() => activateAccount(TOKEN, "abcdefg1"))

  assert.equal(command.path, "/api/auth/activate")
  assert.equal(command.options.method, "POST")
  assert.equal(command.options.credentials, "include")
  assert.equal(new Headers(command.options.headers).get("X-XSRF-TOKEN"), "csrf-synthetic")
  assert.deepEqual(JSON.parse(command.options.body), { token: TOKEN, newPassword: "abcdefg1" })
  assert.doesNotMatch(command.path, new RegExp(TOKEN))
})

test("reset completion uses /api/auth/password-reset/complete with the same payload", async () => {
  const { command } = await captureCommand(() => completePasswordReset(TOKEN, "abcdefg1"))

  assert.equal(command.path, "/api/auth/password-reset/complete")
  assert.equal(command.options.method, "POST")
  assert.deepEqual(JSON.parse(command.options.body), { token: TOKEN, newPassword: "abcdefg1" })
})

test("forgot-password posts only the e-mail and accepts the backend's empty 202", async () => {
  const { command } = await captureCommand(() => requestPasswordReset("pessoa@exemplo.invalid"), 202)

  assert.equal(command.path, "/api/auth/password-reset/request")
  assert.equal(command.options.method, "POST")
  assert.equal(new Headers(command.options.headers).get("X-XSRF-TOKEN"), "csrf-synthetic")
  assert.deepEqual(JSON.parse(command.options.body), { email: "pessoa@exemplo.invalid" })
})

test("activate/reset errors become generic messages without token or API body", () => {
  assert.equal(describeAccountActionError(new ApiError(400, "x")), INVALID_LINK_MESSAGE)
  assert.equal(INVALID_LINK_MESSAGE, "Este link é inválido, expirou ou já foi utilizado.")
  assert.equal(describeAccountActionError(new ApiError(429, "x")), TOO_MANY_ATTEMPTS_MESSAGE)
  assert.equal(TOO_MANY_ATTEMPTS_MESSAGE, "Muitas tentativas. Tente novamente mais tarde.")

  for (const error of [new ApiError(500, `boom ${TOKEN}`, `CODE_${TOKEN}`), new ApiError(403, "x"), new ApiError(404, "x"), new TypeError(`fetch failed ${TOKEN}`), "x"]) {
    const message = describeAccountActionError(error)
    assert.notEqual(message, INVALID_LINK_MESSAGE)
    assert.doesNotMatch(message, new RegExp(TOKEN))
    assert.doesNotMatch(message, /boom|CODE_|fetch failed|ApiError|\d{3}/)
  }
})

test("forgot-password stays neutral: no account enumeration in success or error copy", () => {
  assert.equal(FORGOT_PASSWORD_NEUTRAL_MESSAGE, "Se houver uma conta associada a esse e-mail, enviaremos as instruções de recuperação.")
  const copies = [
    FORGOT_PASSWORD_NEUTRAL_MESSAGE,
    ...[400, 401, 403, 404, 409, 429, 500].map((status) => describePasswordResetRequestError(new ApiError(status, "x"))),
    describePasswordResetRequestError(new TypeError("offline")),
  ]
  for (const copy of copies) {
    assert.doesNotMatch(copy, /não encontrad|não cadastrad|inexistente|não existe|desconhecid|inativ|bloquead/i, copy)
  }
  assert.equal(describePasswordResetRequestError(new ApiError(429, "x")), TOO_MANY_ATTEMPTS_MESSAGE)
})

test("success sends the user to /login with a fixed, token-free notice", () => {
  for (const [action, expected] of [["activate", "Acesso ativado. Entre com seu e-mail e a senha que você criou."], ["reset", "Senha alterada. Entre novamente com sua nova senha."]]) {
    const { to, options } = accountActionSuccessNavigation(action)

    assert.equal(to, "/login")
    assert.equal(options.replace, true)
    assert.doesNotMatch(to, /[?#]/)
    assert.doesNotMatch(JSON.stringify(options), new RegExp(TOKEN))
    assert.equal(loginNoticeFrom(options.state), expected)
  }
  // Só avisos conhecidos: texto arbitrário vindo do histórico não é exibido.
  assert.equal(loginNoticeFrom({ identityNotice: `<b>${TOKEN}</b>` }), null)
  assert.equal(loginNoticeFrom(null), null)
  assert.equal(loginNoticeFrom("activated"), null)
})
