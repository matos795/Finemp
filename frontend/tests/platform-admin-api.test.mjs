import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import {
  inviteFirmUser,
  listAccountingFirms,
  listFirmUsers,
} from "../src/features/platform-admin/api/platform-admin-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("platform administration loads every hierarchy page", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })

    const url = new URL(path, "https://example.invalid")
    const page = Number(url.searchParams.get("page"))

    if (page === 0) {
      return Response.json({
        items: [{ id: "firm-1", name: "A", active: true }],
        page: 0,
        size: 100,
        totalItems: 2,
        totalPages: 2,
      })
    }

    return Response.json({
      items: [{ id: "firm-2", name: "B", active: true }],
      page: 1,
      size: 100,
      totalItems: 2,
      totalPages: 2,
    })
  }

  const result = await listAccountingFirms()

  assert.deepEqual(
    result.items.map((firm) => firm.id),
    ["firm-1", "firm-2"],
  )
  assert.equal(calls.length, 2)
  assert.equal(calls[0].path, "/api/admin/accounting-firms?page=0&size=100")
  assert.equal(calls[1].path, "/api/admin/accounting-firms?page=1&size=100")
  assert.equal(calls[0].options.credentials, "include")
})


test("firm users are scoped to the selected accounting firm", async () => {
  const calls = []

  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })

    return Response.json({
      items: [{
        id: "user-1",
        name: "Cliente Teste",
        email: "cliente@example.invalid",
        active: false,
        invitationPending: true,
      }],
      page: 0,
      size: 100,
      totalItems: 1,
      totalPages: 1,
    })
  }

  const result = await listFirmUsers("firm-1")

  assert.equal(result.items[0].id, "user-1")
  assert.equal(
    calls[0].path,
    "/api/admin/accounting-firms/firm-1/users?page=0&size=100",
  )
})

test("firm invitation uses csrf and never sends a password", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "csrf-token",
      })
    }

    return new Response(null, { status: 204 })
  }

  await inviteFirmUser(
    "firm-1",
    "Pessoa Convidada",
    "pessoa@example.invalid",
    [],
  )

  assert.equal(calls.length, 2)
  assert.equal(calls[0].path, "/api/auth/csrf")
  assert.equal(
    calls[1].path,
    "/api/admin/accounting-firms/firm-1/invitations",
  )
  assert.equal(calls[1].options.method, "POST")
  assert.equal(
    new Headers(calls[1].options.headers).get("X-CSRF-TOKEN"),
    "csrf-token",
  )

  const body = JSON.parse(calls[1].options.body)
  assert.deepEqual(body, {
    name: "Pessoa Convidada",
    email: "pessoa@example.invalid",
    permissions: [],
  })
  assert.equal("password" in body, false)
})
