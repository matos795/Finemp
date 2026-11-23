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

export interface AccountingSource {
    id: string
    companyId: string
    type: "QUESTOR"
    externalReference: string | null
    active: boolean
    connectionMode: "DIRECT" | "CONNECTOR"
}

export interface ConnectorCredential {
    id: string
    accountingFirmId: string
    accountingSourceId: string
    label: string
    createdAt: string
    expiresAt: string
    revokedAt: string | null
    version: number
}

export interface ConnectorSecret {
    credential: ConnectorCredential
    secret: string
}

export type FirmPermission =
    | "HIERARCHY_VIEW"
    | "HIERARCHY_MANAGE"
    | "ACCOUNTING_SOURCE_VIEW"
    | "ACCOUNTING_SOURCE_MANAGE"
    | "USER_ACCESS_MANAGE"
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


export async function listAccountingSources(
    accountingFirmId: string,
    clientId: string,
    companyId: string,
) {
    return listAllPages<AccountingSource>(
        `/api/accounting-firms/${accountingFirmId}/clients/${clientId}/companies/${companyId}/accounting-sources`,
    )
}

export async function createAccountingSource(
    accountingFirmId: string,
    clientId: string,
    companyId: string,
    externalReference: string,
) {
    return apiRequest<AccountingSource>(
        `/api/accounting-firms/${accountingFirmId}/clients/${clientId}/companies/${companyId}/accounting-sources`,
        {
            method: "POST",
            body: JSON.stringify({
                type: "QUESTOR",
                externalReference: externalReference.trim() || null,
            }),
        },
    )
}

export async function updateAccountingSourceExternalReference(
    accountingFirmId: string,
    clientId: string,
    companyId: string,
    accountingSourceId: string,
    externalReference: string,
) {
    return apiRequest<AccountingSource>(
        `/api/accounting-firms/${accountingFirmId}/clients/${clientId}/companies/${companyId}/accounting-sources/${accountingSourceId}/external-reference`,
        {
            method: "PUT",
            body: JSON.stringify({
                externalReference: externalReference.trim(),
            }),
        },
    )
}

export async function listConnectorCredentials(
    accountingFirmId: string,
) {
    return listAllPages<ConnectorCredential>(
        `/api/admin/accounting-firms/${accountingFirmId}/connectors`,
    )
}

export async function issueConnectorCredential(
    accountingFirmId: string,
    accountingSourceId: string,
    label: string,
) {
    return apiRequest<ConnectorSecret>(
        `/api/admin/accounting-firms/${accountingFirmId}/connectors`,
        {
            method: "POST",
            cache: "no-store",
            body: JSON.stringify({
                accountingSourceId,
                label: label.trim(),
            }),
        },
    )
}

export async function rotateConnectorCredential(
    accountingFirmId: string,
    credentialId: string,
) {
    return apiRequest<ConnectorSecret>(
        `/api/admin/accounting-firms/${accountingFirmId}/connectors/${credentialId}/rotate`,
        {
            method: "POST",
            cache: "no-store",
        },
    )
}

export async function revokeConnectorCredential(
    accountingFirmId: string,
    credentialId: string,
) {
    return apiRequest<void>(
        `/api/admin/accounting-firms/${accountingFirmId}/connectors/${credentialId}`,
        {
            method: "DELETE",
        },
    )
}


export type SyncRunStatus =
    | "REQUESTED"
    | "RUNNING"
    | "SUCCEEDED"
    | "FAILED"
    | "CANCELLED"

export interface SyncRun {
    id: string
    accountingSourceId: string
    requestedByUserId: string
    mode: "INITIAL" | "INCREMENTAL" | "RECONCILIATION"
    status: SyncRunStatus
    startDate: string
    endDate: string
    requestedAt: string
    startedAt: string | null
    finishedAt: string | null
    readCount: number | null
    importedCount: number | null
    skippedCount: number | null
    failureCode: string | null
}

export async function listSyncRuns(
    accountingSourceId: string,
    page = 0,
    size = 10,
) {
    return apiRequest<PagedResponse<SyncRun>>(
        `/api/accounting-sources/${accountingSourceId}/sync-runs?page=${page}&size=${size}`,
    )
}

export async function requestInitialSyncRun(
    accountingSourceId: string,
    idempotencyKey: string,
    startDate: string,
    endDate: string,
) {
    return apiRequest<SyncRun>(
        `/api/accounting-sources/${accountingSourceId}/sync-runs`,
        {
            method: "POST",
            headers: {
                "Idempotency-Key": idempotencyKey,
            },
            body: JSON.stringify({
                mode: "INITIAL",
                startDate,
                endDate,
            }),
        },
    )
}
