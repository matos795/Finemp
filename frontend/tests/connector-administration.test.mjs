import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createAccountingSource,
  issueConnectorCredential,
  listAccountingSources,
  listConnectorCredentials,
  revokeConnectorCredential,
  rotateConnectorCredential,
} from "../src/features/platform-admin/api/platform-admin-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test("connector administration API stays scoped to firm hierarchy and source", async () => {
  const calls = []

  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "csrf-token",
      })
    }

    if (String(path).includes("/accounting-sources") && options.method !== "POST") {
      return Response.json({
        items: [],
        page: 0,
        size: 100,
        totalItems: 0,
        totalPages: 1,
      })
    }

    const url = new URL(path, "https://example.invalid")

    if (url.pathname.endsWith("/connectors") && options.method !== "POST") {
      return Response.json({
        items: [],
        page: 0,
        size: 100,
        totalItems: 0,
        totalPages: 1,
      })
    }

    if (options.method === "DELETE") {
      return new Response(null, { status: 204 })
    }

    if (String(path).endsWith("/rotate")) {
      return Response.json({
        credential: {
          id: "cred-1",
          accountingFirmId: "firm-1",
          accountingSourceId: "source-1",
          label: "Connector Questor",
          createdAt: "2026-11-14T00:00:00Z",
          expiresAt: "2027-02-12T00:00:00Z",
          revokedAt: null,
          version: 2,
        },
        secret: "one-time-rotated",
      })
    }

    if (String(path).endsWith("/connectors")) {
      return Response.json({
        credential: {
          id: "cred-1",
          accountingFirmId: "firm-1",
          accountingSourceId: "source-1",
          label: "Connector Questor",
          createdAt: "2026-11-14T00:00:00Z",
          expiresAt: "2027-02-12T00:00:00Z",
          revokedAt: null,
          version: 0,
        },
        secret: "one-time-issued",
      })
    }

    return Response.json({
      id: "source-1",
      companyId: "company-1",
      type: "QUESTOR",
      externalReference: "synthetic-source",
      active: true,
      connectionMode: "DIRECT",
    })
  }

  await listAccountingSources("firm-1", "client-1", "company-1")
  await listConnectorCredentials("firm-1")
  await createAccountingSource("firm-1", "client-1", "company-1", "synthetic-source")
  await issueConnectorCredential("firm-1", "source-1", "Connector Questor")
  await rotateConnectorCredential("firm-1", "cred-1")
  await revokeConnectorCredential("firm-1", "cred-1")

  assert.equal(
    calls[0].path,
    "/api/accounting-firms/firm-1/clients/client-1/companies/company-1/accounting-sources?page=0&size=100",
  )
  assert.equal(
    calls[1].path,
    "/api/admin/accounting-firms/firm-1/connectors?page=0&size=100",
  )

  const writes = calls.filter((call) =>
    call.path !== "/api/auth/csrf" &&
    ["POST", "DELETE"].includes(call.options.method),
  )

  const sourceCreate = writes.find((call) =>
    String(call.path).includes("/accounting-sources"),
  )
  assert.deepEqual(JSON.parse(sourceCreate.options.body), {
    type: "QUESTOR",
    externalReference: "synthetic-source",
  })

  const issue = writes.find((call) =>
    String(call.path).endsWith("/connectors") &&
    call.options.body &&
    JSON.parse(call.options.body).accountingSourceId === "source-1",
  )
  assert.deepEqual(JSON.parse(issue.options.body), {
    accountingSourceId: "source-1",
    label: "Connector Questor",
  })

  for (const call of writes) {
    const body = call.options.body ? String(call.options.body) : ""
    assert.doesNotMatch(body, /password|dsn|firebird|fdb|jdbc|username/i)

    const headers = new Headers(call.options.headers)
    assert.equal(headers.get("X-CSRF-TOKEN"), "csrf-token")
  }
})

test("connector UI keeps the one-time secret only in component memory and discards it on scope change", () => {
  const section = readFileSync(
    new URL("../src/features/platform-admin/components/connector-administration-section.tsx", import.meta.url),
    "utf8",
  )
  const page = readFileSync(
    new URL("../src/features/platform-admin/pages/platform-admin-page.tsx", import.meta.url),
    "utf8",
  )

  assert.ok(section.includes("useState<string | null>(null)"))
  assert.ok(section.includes("setOneTimeSecret(issued.secret)"))
  assert.ok(section.includes("setOneTimeSecret(rotated.secret)"))
  assert.ok(section.includes("setOneTimeSecret(null)"))
  assert.match(section, /accountingFirmId, clientId, companyId/)
  assert.match(section, /selectedSourceId/)

  assert.doesNotMatch(section, /localStorage|sessionStorage/)
  assert.doesNotMatch(section, /console\.(?:log|info|warn|error)/)

  assert.match(page, /ConnectorAdministrationSection/)
  assert.match(page, /selectedFirmId/)
  assert.match(page, /selectedClientId/)
  assert.match(page, /selectedCompanyId/)
})

test("connector UI never asks for ERP credentials and exposes only safe machine credential actions", () => {
  const section = readFileSync(
    new URL("../src/features/platform-admin/components/connector-administration-section.tsx", import.meta.url),
    "utf8",
  )

  for (const label of [
    "Criar fonte Questor",
    "Emitir credencial Connector",
    "Rotacionar",
    "Revogar",
    "Segredo exibido uma única vez",
  ]) {
    assert.ok(section.includes(label), label)
  }

  assert.match(section, /não recebe senha, usuário, caminho FDB ou DSN/)
  assert.doesNotMatch(section, /type="password"/)
})
