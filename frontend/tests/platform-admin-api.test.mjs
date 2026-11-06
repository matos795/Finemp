import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { listAccountingFirms } from "../src/features/platform-admin/api/platform-admin-api.ts"

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
