import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    CompanyAccessRole,
    CompanyPermission,
    PlatformPermission,
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
    const separator = path.includes("?") ? "&" : "?"
    const firstPage = await apiRequest<PagedResponse<T>>(
        `${path}${separator}page=0&size=100`,
    )

    const items = [...firstPage.items]

    for (let page = 1; page < firstPage.totalPages; page += 1) {
        const response = await apiRequest<PagedResponse<T>>(
            `${path}${separator}page=${page}&size=100`,
        )

        items.push(...response.items)
    }

    return {
        ...firstPage,
        items,
        page: 0,
        size: items.length,
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

export async function deleteAccountingFirm(accountingFirmId: string) {
    return apiRequest<void>(
        `/api/admin/accounting-firms/${accountingFirmId}`,
        {
            method: "DELETE",
        },
    )
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

export async function deleteClient(
    accountingFirmId: string,
    clientId: string,
) {
    return apiRequest<void>(
        `/api/accounting-firms/${accountingFirmId}/clients/${clientId}`,
        {
            method: "DELETE",
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

export async function listPlatformUsersPage(
    search = "",
    page = 0,
    size = 20,
) {
    const params = new URLSearchParams({
        search: search.trim(),
        page: String(page),
        size: String(size),
    })

    return apiRequest<PagedResponse<PlatformUser>>(
        `/api/admin/users?${params.toString()}`,
    )
}

export async function listFirmUsers(accountingFirmId: string) {
    return listAllPages<PlatformUser>(
        `/api/admin/accounting-firms/${accountingFirmId}/users`,
    )
}

export async function inviteFirmUser(
    accountingFirmId: string,
    name: string,
    email: string,
    permissions: FirmPermission[],
) {
    return apiRequest<void>(
        `/api/admin/accounting-firms/${accountingFirmId}/invitations`,
        {
            method: "POST",
            body: JSON.stringify({
                name,
                email,
                permissions,
            }),
        },
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

export function getUserPlatformPermissions(id: string) {
    return apiRequest<{ user: PlatformUser; platformPermissions: PlatformPermission[] }>("/api/admin/users/" + id)
}

export function replaceUserPlatformPermissions(id: string, permissions: PlatformPermission[]) {
    return apiRequest<void>("/api/admin/users/" + id + "/platform-permissions", {
        method: "PUT",
        body: JSON.stringify({ permissions }),
    })
}
