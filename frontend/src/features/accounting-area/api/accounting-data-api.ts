import { apiRequest } from "../../../lib/api/api-client.ts"

/** Item de GET /api/companies/{companyId}/accounting-accounts (#442). */
export interface ImportedAccount {
    accountId: string
    accountingSourceId: string
    accountKey: string
    description: string
    status: "PENDING" | "CLASSIFIED"
    classificationId: string | null
    dreClassification: string | null
    dreOpenClassification: string | null
    ebitdaClassification: string | null
}

export interface ImportedAccountsPage {
    universe: "IMPORTED_ACCOUNTS"
    items: ImportedAccount[]
    page: number
    size: number
    totalItems: number
    totalPages: number
    summary: { total: number; classified: number; pending: number }
}

export interface DreCatalogEntry {
    id: string
    dreClassification: string
    dreOpenClassification: string
    ebitdaClassification: string
}

/** Item de GET /api/companies/{companyId}/accounting-movements/search (#444). */
export interface MovementItem {
    id: string
    accountingDate: string
    accountingSourceId: string
    accountId: string
    accountKey: string
    accountDescription: string | null
    side: "DEBIT" | "CREDIT"
    amount: string
    history: string | null
    sourceEntryKey: string
}

export interface MovementSearchPage {
    items: MovementItem[]
    page: number
    size: number
    totalItems: number
    totalPages: number
    totals: { debit: string; credit: string }
    notSynchronized: string[]
}

const companyPath = (companyId: string) => `/api/companies/${encodeURIComponent(companyId)}`

export function searchImportedAccounts(
    companyId: string,
    filters: { status: "PENDING" | null; search: string; page: number; size: number },
) {
    const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })

    if (filters.status) params.set("status", filters.status)
    if (filters.search.trim()) params.set("search", filters.search.trim())

    return apiRequest<ImportedAccountsPage>(`${companyPath(companyId)}/accounting-accounts?${params}`)
}

export function listDreCatalog() {
    return apiRequest<DreCatalogEntry[]>("/api/dre-classifications")
}

export function classifyImportedAccount(companyId: string, accountId: string, classificationId: string) {
    return apiRequest<void>(
        `${companyPath(companyId)}/accounting-accounts/${encodeURIComponent(accountId)}/classification`,
        { method: "PUT", body: JSON.stringify({ classificationId }) },
    )
}

export function searchMovements(
    companyId: string,
    filters: { accountId: string; startDate: string; endDate: string; page: number; size: number },
) {
    const params = new URLSearchParams({
        accountId: filters.accountId,
        startDate: filters.startDate,
        endDate: filters.endDate,
        page: String(filters.page),
        size: String(filters.size),
    })

    return apiRequest<MovementSearchPage>(`${companyPath(companyId)}/accounting-movements/search?${params}`)
}

/** GET /api/companies/{companyId}/dre (#446): agregação autoritativa do backend. */
export interface AccountingDre {
    unclassifiedAccountCount: number
    groups: {
        dreClassification: string
        total: string
        groups: {
            dreOpenClassification: string
            total: string
            lines: { ebitdaClassification: string; total: string }[]
        }[]
    }[]
    classifiedTotal: string
    coverage: { accountingSourceId: string; syncedStartDate: string | null; syncedEndDate: string | null }[]
}

export function getAccountingDre(companyId: string, startDate: string, endDate: string) {
    const params = new URLSearchParams({ startDate, endDate })

    return apiRequest<AccountingDre>(`${companyPath(companyId)}/dre?${params}`)
}
