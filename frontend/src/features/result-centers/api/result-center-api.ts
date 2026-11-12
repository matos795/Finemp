import { apiRequest } from "../../../lib/api/api-client"

export type ResultCenterType = "GROUP" | "ANALYTICAL"

export interface ResultCenter {
    id: string
    companyId: string
    parentId: string | null
    code: string
    name: string
    active: boolean
    hasChildren: boolean
    version: number
    type: ResultCenterType
}

export interface ResultCenterPage {
    items: ResultCenter[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export function listResultCenters(
    companyId: string,
    search = "",
    page = 0,
    size = 50,
) {
    const params = new URLSearchParams({
        search: search.trim(),
        page: String(page),
        size: String(size),
    })

    return apiRequest<ResultCenterPage>(
        `/api/companies/${companyId}/result-centers?${params.toString()}`,
    )
}

export function createResultCenter(
    companyId: string,
    input: { code: string; name: string; type: ResultCenterType },
) {
    return apiRequest<ResultCenter>(
        `/api/companies/${companyId}/result-centers`,
        {
            method: "POST",
            body: JSON.stringify(input),
        },
    )
}

export function updateResultCenter(
    companyId: string,
    centerId: string,
    input: {
        name: string
        active: boolean
        expectedVersion: number
        type: ResultCenterType
    },
) {
    return apiRequest<ResultCenter>(
        `/api/companies/${companyId}/result-centers/${centerId}`,
        {
            method: "PUT",
            body: JSON.stringify(input),
        },
    )
}
