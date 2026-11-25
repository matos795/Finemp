import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    Employee,
    EmployeeFilter,
    EmployeePage,
    EmployeeValues,
    JobPosition,
    JobPositionFilter,
    JobPositionPage,
    JobPositionValues,
} from "../types/people.types.ts"

export const PEOPLE_PAGE_SIZE = 50

function companyPath(companyId: string, resource: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/${resource}`
}

export function listJobPositions(
    companyId: string,
    filter: JobPositionFilter,
    page: number,
    signal?: AbortSignal,
    size = PEOPLE_PAGE_SIZE,
): Promise<JobPositionPage> {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    const search = filter.search.trim()
    if (search) params.set("search", search)
    if (filter.active) params.set("active", filter.active)
    return apiRequest<JobPositionPage>(
        `${companyPath(companyId, "job-positions")}?${params}`,
        { signal, cache: "no-store" },
    )
}

export function createJobPosition(companyId: string, values: JobPositionValues): Promise<JobPosition> {
    return apiRequest<JobPosition>(companyPath(companyId, "job-positions"), {
        method: "POST",
        body: JSON.stringify({ ...values, expectedVersion: null }),
    })
}

export function updateJobPosition(
    companyId: string,
    position: JobPosition,
    values: JobPositionValues,
): Promise<JobPosition> {
    return apiRequest<JobPosition>(
        `${companyPath(companyId, "job-positions")}/${encodeURIComponent(position.id)}`,
        { method: "PUT", body: JSON.stringify({ ...values, expectedVersion: position.version }) },
    )
}

export function listEmployees(
    companyId: string,
    filter: EmployeeFilter,
    page: number,
    signal?: AbortSignal,
): Promise<EmployeePage> {
    const params = new URLSearchParams({ page: String(page), size: String(PEOPLE_PAGE_SIZE) })
    const search = filter.search.trim()
    if (search) params.set("search", search)
    if (filter.employmentType) params.set("employmentType", filter.employmentType)
    if (filter.status) params.set("status", filter.status)
    if (filter.jobPositionId) params.set("jobPositionId", filter.jobPositionId)
    if (filter.resultCenterId) params.set("resultCenterId", filter.resultCenterId)
    return apiRequest<EmployeePage>(
        `${companyPath(companyId, "employees")}?${params}`,
        { signal, cache: "no-store" },
    )
}

export function createEmployee(companyId: string, values: EmployeeValues): Promise<Employee> {
    return apiRequest<Employee>(companyPath(companyId, "employees"), {
        method: "POST",
        body: JSON.stringify({ ...values, expectedVersion: null }),
    })
}

export function updateEmployee(
    companyId: string,
    employee: Employee,
    values: EmployeeValues,
): Promise<Employee> {
    return apiRequest<Employee>(
        `${companyPath(companyId, "employees")}/${encodeURIComponent(employee.id)}`,
        { method: "PUT", body: JSON.stringify({ ...values, expectedVersion: employee.version }) },
    )
}
