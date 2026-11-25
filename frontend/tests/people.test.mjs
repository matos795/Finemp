import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createEmployee,
  createJobPosition,
  listEmployees,
  listJobPositions,
  updateEmployee,
  updateJobPosition,
} from "../src/features/people/api/people-api.ts"
import {
  findNavigationByPath,
  navigationModules,
} from "../src/components/layout/navigation.ts"
import { employmentTypeOptions } from "../src/features/people/types/people.types.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function router() {
  return readFileSync(new URL("../src/app/router/app-router.tsx", import.meta.url), "utf8")
}

test("people screens are published only for people permissions", () => {
  for (const [path, id, page] of [
    ["/people/employees", "func", "EmployeesPage"],
    ["/people/job-positions", "pesCargo", "JobPositionsPage"],
  ]) {
    const found = findNavigationByPath(navigationModules, path)
    assert.equal(found.module.id, "pes")
    assert.equal(found.item.id, id)
    assert.deepEqual(found.item.permissions, ["EMPLOYEE_VIEW", "EMPLOYEE_MANAGE"])
    assert.match(
      router(),
      new RegExp(`path="${path}"[\\s\\S]*?EMPLOYEE_VIEW[\\s\\S]*?EMPLOYEE_MANAGE[\\s\\S]*?<${page}`),
    )
  }
})

test("people roadmap items that need rules stay without route", () => {
  const people = navigationModules.find((module) => module.id === "pes")
  for (const id of ["pesCusto", "pesAval", "pesPdi", "pes9box"]) {
    assert.equal(people.items.find((item) => item.id === id).path, undefined, id)
  }
})

test("people reads keep filters and pagination on the server", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    return Response.json({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 })
  }

  await listEmployees("company-1", {
    search: " pessoa ",
    employmentType: "CLT",
    status: "ACTIVE",
    jobPositionId: "position-1",
    resultCenterId: "",
  }, 1)
  await listJobPositions("company-1", { search: " analista ", active: "true" }, 0)

  assert.equal(
    calls[0].path,
    "/api/companies/company-1/employees?page=1&size=50&search=pessoa&employmentType=CLT&status=ACTIVE&jobPositionId=position-1",
  )
  assert.equal(
    calls[1].path,
    "/api/companies/company-1/job-positions?page=0&size=50&search=analista&active=true",
  )
  assert.equal(calls[0].options.cache, "no-store")
})

test("people commands send optimistic version only on update and no personal extras", async () => {
  const calls = []
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })
    if (path === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" })
    return Response.json({ id: "x", version: 3 })
  }

  const employee = {
    fullName: "Pessoa Sintética",
    employmentType: "CLT",
    jobPositionId: null,
    resultCenterId: null,
    hiredOn: "2026-01-05",
    terminatedOn: null,
  }
  await createEmployee("company-1", employee)
  await updateEmployee("company-1", { id: "employee-1", version: 2 }, employee)
  await createJobPosition("company-1", { title: "Analista", description: null, active: true })
  await updateJobPosition("company-1", { id: "position-1", version: 5 }, { title: "Analista", description: null, active: false })

  const writes = calls.filter((call) => call.path !== "/api/auth/csrf")
  const bodies = writes.map((call) => JSON.parse(call.options.body))

  assert.equal(writes[0].options.method, "POST")
  assert.equal(bodies[0].expectedVersion, null)
  assert.equal(writes[1].path, "/api/companies/company-1/employees/employee-1")
  assert.equal(bodies[1].expectedVersion, 2)
  assert.equal(bodies[3].expectedVersion, 5)
  for (const body of bodies) {
    for (const field of ["salary", "cpf", "birthDate", "document", "email", "phone"]) {
      assert.equal(field in body, false, field)
    }
  }
})

test("employment types follow the homologated regimes only", () => {
  assert.deepEqual(employmentTypeOptions.map((option) => option.value), ["CLT", "APR", "PRO", "EST", "PJ"])
})

test("people pages use the shared shell patterns and guard writes by company and permission", () => {
  for (const file of ["employees-page.tsx", "job-positions-page.tsx"]) {
    const source = readFileSync(new URL(`../src/features/people/pages/${file}`, import.meta.url), "utf8")
    assert.match(source, /components\/finemp\/modal/)
    assert.match(source, /<Modal/)
    assert.match(source, /<Pager/)
    assert.match(source, /className="tblwrap"/)
    assert.match(source, /className="filters"/)
    assert.match(source, /<QueryError/)
    assert.ok(source.includes('Boolean(companyId) && hasPermission("EMPLOYEE_MANAGE")'), file)
    assert.match(source, /onClose=\{save\.isPending \? undefined : onClose\}/)
    assert.match(source, /invalidateQueries/)
  }
})

test("position options load every page, not only the first", async () => {
  const { listAllJobPositions } = await import("../src/features/people/api/people-api.ts")
  const calls = []
  globalThis.fetch = async (path) => {
    calls.push(String(path))
    const page = Number(new URL(String(path), "http://x").searchParams.get("page"))
    return Response.json({
      items: [{ id: `position-${page}`, title: `Cargo ${page}`, description: null, active: true, version: 0 }],
      page, size: 100, totalItems: 3, totalPages: 3,
    })
  }

  const items = await listAllJobPositions("company-1", "true")

  assert.deepEqual(items.map((item) => item.id), ["position-0", "position-1", "position-2"])
  assert.ok(calls.every((call) => call.includes("size=100") && call.includes("active=true")))
  const source = readFileSync(new URL("../src/features/people/pages/employees-page.tsx", import.meta.url), "utf8")
  assert.doesNotMatch(source, /listJobPositions\(/)
  assert.match(source, /listAllJobPositions\(companyId!?, "true"\)/)
})
