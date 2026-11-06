import { apiRequest } from "../../../lib/api/api-client"
import type {
    CompanyAccessRole,
    CompanyPermission,
} from "../../auth/types/auth.types"

export interface PagedResponse<T> {
    items: T[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export interface AccountingFirm {
    id: string
    name: string
    active: boolean
}

export interface Client {
    id: string
    accountingFirmId: string
    name: string
    active: boolean
}

export interface Company {
    id: string
    clientId: string | null
    legalName: string
    cnpj: string
}

export interface PlatformUser {
    id: string
    name: string
    email: string
    active: boolean
    invitationPending: boolean
}

export type FirmPermission =
    | "HIERARCHY_MANAGE"
    | "USER_ACCESS_MANAGE"
    | "ACCOUNTING_SOURCE_MANAGE"
    | "CONNECTOR_MANAGE"


async function listAllPages<T>(path: string): Promise<PagedResponse<T>> {
    const items: T[] = []
    let page = 0
    let totalPages = 1
    let totalItems = 0

    do {
        const separator = path.includes("?") ? "&" : "?"
        const response = await apiRequest<PagedResponse<T>>(
            `${path}${separator}page=${page}&size=100`,
        )

        items.push(...response.items)
        totalPages = response.totalPages
        totalItems = response.totalItems
        page += 1
    } while (page < totalPages)

    return {
        items,
        page: 0,
        size: items.length,
        totalItems,
        totalPages,
    }
}

export async function listAccountingFirms() {
    return listAllPages<AccountingFirm>(
        "/api/admin/accounting-firms",
    )
}

export async function createAccountingFirm(name: string) {
    return apiRequest<AccountingFirm>("/api/admin/accounting-firms", {
        method: "POST",
        body: JSON.stringify({ name }),
    })
}

export async function listClients(accountingFirmId: string) {
    return listAllPages<Client>(
        `/api/accounting-firms/${accountingFirmId}/clients`,
    )
}

export async function createClient(
    accountingFirmId: string,
    name: string,
) {
    return apiRequest<Client>(
        `/api/accounting-firms/${accountingFirmId}/clients`,
        {
            method: "POST",
            body: JSON.stringify({ name }),
        },
    )
}

export async function listCompanies(
    accountingFirmId: string,
    clientId: string,
) {
    return listAllPages<Company>(
        `/api/accounting-firms/${accountingFirmId}/clients/${clientId}/companies`,
    )
}

export async function createCompany(
    accountingFirmId: string,
    clientId: string,
    legalName: string,
    cnpj: string,
) {
    return apiRequest<Company>(
        `/api/accounting-firms/${accountingFirmId}/clients/${clientId}/companies`,
        {
            method: "POST",
            body: JSON.stringify({
                legalName,
                cnpj,
            }),
        },
    )
}

export async function listPlatformUsers() {
    return listAllPages<PlatformUser>(
        "/api/admin/users",
    )
}

export async function replaceFirmAccess(
    accountingFirmId: string,
    userId: string,
    permissions: FirmPermission[],
) {
    return apiRequest<void>(
        `/api/admin/accounting-firms/${accountingFirmId}/users/${userId}`,
        {
            method: "PUT",
            body: JSON.stringify({ permissions }),
        },
    )
}

export async function replaceCompanyAccess(
    accountingFirmId: string,
    clientId: string,
    companyId: string,
    userId: string,
    role: CompanyAccessRole,
    permissions: CompanyPermission[],
) {
    return apiRequest<void>(
        `/api/admin/accounting-firms/${accountingFirmId}/clients/${clientId}/companies/${companyId}/users/${userId}`,
        {
            method: "PUT",
            body: JSON.stringify({
                role,
                costCenterScope: "ALL",
                permissions,
            }),
        },
    )
}
