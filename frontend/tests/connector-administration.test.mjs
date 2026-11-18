import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createAccountingSource,
  issueConnectorCredential,
  listAccountingSources,
  listConnectorCredentials,
  listSyncRuns,
  requestInitialSyncRun,
  revokeConnectorCredential,
  rotateConnectorCredential,
  updateAccountingSourceExternalReference,
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

    if (String(path).includes("/sync-runs") && options.method === "POST") {
      return Response.json({
        id: "run-1",
        accountingSourceId: "source-1",
        requestedByUserId: "user-1",
        mode: "INITIAL",
        status: "REQUESTED",
        startDate: "2026-07-01",
        endDate: "2026-07-01",
        requestedAt: "2026-11-18T17:00:00Z",
        startedAt: null,
        finishedAt: null,
        readCount: null,
        importedCount: null,
        skippedCount: null,
        failureCode: null,
      })
    }

    if (String(path).includes("/sync-runs")) {
      return Response.json({
        items: [],
        page: 0,
        size: 10,
        totalItems: 0,
        totalPages: 1,
      })
    }

    if (String(path).endsWith("/external-reference") && options.method === "PUT") {
      return Response.json({
        id: "source-1",
        companyId: "company-1",
        type: "QUESTOR",
        externalReference: "57",
        active: true,
        connectionMode: "CONNECTOR",
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
  await listSyncRuns("source-1")
  await requestInitialSyncRun(
    "source-1",
    "00000000-0000-4000-8000-000000000001",
    "2026-07-01",
    "2026-07-01",
  )
  await createAccountingSource("firm-1", "client-1", "company-1", "synthetic-source")
  await updateAccountingSourceExternalReference(
    "firm-1",
    "client-1",
    "company-1",
    "source-1",
    " 57 ",
  )
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
    ["POST", "PUT", "DELETE"].includes(call.options.method),
  )

  const syncRequest = writes.find((call) =>
    String(call.path).endsWith("/accounting-sources/source-1/sync-runs"),
  )
  assert.deepEqual(JSON.parse(syncRequest.options.body), {
    mode: "INITIAL",
    startDate: "2026-07-01",
    endDate: "2026-07-01",
  })
  assert.equal(
    new Headers(syncRequest.options.headers).get("Idempotency-Key"),
    "00000000-0000-4000-8000-000000000001",
  )

  const sourceCreate = writes.find((call) =>
    String(call.path).includes("/accounting-sources") &&
    !String(call.path).includes("/sync-runs") &&
    !String(call.path).endsWith("/external-reference"),
  )
  assert.deepEqual(JSON.parse(sourceCreate.options.body), {
    type: "QUESTOR",
    externalReference: "synthetic-source",
  })

  const sourceUpdate = writes.find((call) =>
    String(call.path).endsWith("/accounting-sources/source-1/external-reference"),
  )
  assert.deepEqual(JSON.parse(sourceUpdate.options.body), {
    externalReference: "57",
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
    "Salvar código Questor",
    "CODIGOEMPRESA",
    "Emitir credencial Connector",
    "Rotacionar",
    "Revogar",
    "Segredo exibido uma única vez",
  ]) {
    assert.ok(section.includes(label), label)
  }

  assert.match(section, /não recebe senha, usuário, caminho FDB ou DSN/)
  assert.match(section, /ConnectorSyncControl/)
  assert.doesNotMatch(section, /type="password"/)
})


test("connector sync control exposes only the currently supported initial mode", () => {
  const section = readFileSync(
    new URL("../src/features/platform-admin/components/connector-sync-control.tsx", import.meta.url),
    "utf8",
  )

  assert.match(section, /requestInitialSyncRun/)
  assert.match(section, /crypto\.randomUUID\(\)/)
  assert.match(section, /type="date"/)
  assert.match(section, /startDate <= endDate/)
  assert.match(section, /Sincronizar agora/)
  assert.match(section, /Nesta fase, apenas o modo inicial está suportado/)
  assert.doesNotMatch(section, /mode:\s*"INCREMENTAL"|mode:\s*"RECONCILIATION"/)
  assert.doesNotMatch(section, /localStorage|sessionStorage|console\.(?:log|info|warn|error)/)
})
