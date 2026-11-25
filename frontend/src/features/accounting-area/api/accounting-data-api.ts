import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    AccountingDataset,
    ChartAvailability,
    DatasetCoverage,
    MovementSourceMetadata,
} from "../lib/accounting-v2.ts"

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

/**
 * Item de GET /api/companies/{companyId}/accounting-movements/search (#444).
 * `sourceMetadataStatus`/`sourceMetadata` vieram no #509 (aditivos).
 */
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
    sourceMetadataStatus?: "SYNCHRONIZED" | "NOT_SYNCHRONIZED"
    sourceMetadata?: MovementSourceMetadata | null
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
    filters: { status: "PENDING" | null; search: string; page: number; size: number; accountingSourceId?: string },
) {
    const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })

    if (filters.accountingSourceId) params.set("accountingSourceId", filters.accountingSourceId)
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
    filters: {
        accountId?: string
        accountingSourceId?: string
        startDate: string
        endDate: string
        page: number
        size: number
    },
) {
    const params = new URLSearchParams()

    if (filters.accountId) params.set("accountId", filters.accountId)
    if (filters.accountingSourceId) params.set("accountingSourceId", filters.accountingSourceId)
    params.set("startDate", filters.startDate)
    params.set("endDate", filters.endDate)
    params.set("page", String(filters.page))
    params.set("size", String(filters.size))

    return apiRequest<MovementSearchPage>(`${companyPath(companyId)}/accounting-movements/search?${params}`)
}

/** GET /accounting-chart (#509): plano completo só com snapshot de CHART publicado. */
export interface AccountingChartPage {
    accountingSourceId: string
    availability: ChartAvailability
    coverage: { dataset: "CHART"; publishedAt: string | null }
    items: {
        accountId: string
        sourceAccountId: string
        classificationCode: string
        description: string
        sourceCodes: {
            accountType: string | null
            calculationType: string | null
            balanceNature: string | null
            balanceControl: string | null
            showInBalanceSheet: string | null
            nativeDreCode: string | null
            nativeDreAccount: string | null
        }
    }[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export function getAccountingChart(
    companyId: string,
    filters: { accountingSourceId: string; search: string; page: number; size: number },
) {
    const params = new URLSearchParams({
        accountingSourceId: filters.accountingSourceId,
        page: String(filters.page),
        size: String(filters.size),
    })

    if (filters.search.trim()) params.set("search", filters.search.trim())

    return apiRequest<AccountingChartPage>(`${companyPath(companyId)}/accounting-chart?${params}`)
}

export type DebitCreditGranularity = "DAILY" | "MONTHLY"

/** GET /accounting-debit-credit-totals (#509): totais D/C do período, nunca saldo. */
export interface DebitCreditTotalsPage {
    accountingSourceId: string
    granularity: DebitCreditGranularity
    startDate: string
    endDate: string
    items: {
        period: string
        establishmentCode: string
        sourceAccountId: string
        accountId: string
        entryTypeCode: string
        debitTotal: string
        creditTotal: string
    }[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export function getDebitCreditTotals(
    companyId: string,
    filters: {
        accountingSourceId: string
        granularity: DebitCreditGranularity
        startDate: string
        endDate: string
        establishmentCode: string
        sourceAccountId: string
        page: number
        size: number
    },
) {
    const params = new URLSearchParams({
        accountingSourceId: filters.accountingSourceId,
        granularity: filters.granularity,
        startDate: filters.startDate,
        endDate: filters.endDate,
        page: String(filters.page),
        size: String(filters.size),
    })

    if (filters.establishmentCode.trim()) params.set("establishmentCode", filters.establishmentCode.trim())
    if (filters.sourceAccountId.trim()) params.set("sourceAccountId", filters.sourceAccountId.trim())

    return apiRequest<DebitCreditTotalsPage>(`${companyPath(companyId)}/accounting-debit-credit-totals?${params}`)
}

/** GET /accounting-dataset-coverage (#509): o que já foi importado, nunca "sincronizado até". */
export function getDatasetCoverage(
    companyId: string,
    filters: { accountingSourceId: string; dataset: AccountingDataset; establishmentCode?: string },
) {
    const params = new URLSearchParams({ accountingSourceId: filters.accountingSourceId, dataset: filters.dataset })

    if (filters.establishmentCode?.trim()) params.set("establishmentCode", filters.establishmentCode.trim())

    return apiRequest<DatasetCoverage>(`${companyPath(companyId)}/accounting-dataset-coverage?${params}`)
}

/** GET /accounting-origin-catalog (#509): código + descrição técnica, sem significado de negócio. */
export interface OriginCatalog {
    accountingSourceId: string
    availability: "AVAILABLE" | "NOT_PUBLISHED"
    publishedAt: string | null
    items: { originCode: string; description: string }[]
}

export function getOriginCatalog(companyId: string, accountingSourceId: string) {
    const params = new URLSearchParams({ accountingSourceId })

    return apiRequest<OriginCatalog>(`${companyPath(companyId)}/accounting-origin-catalog?${params}`)
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
    coverage: {
        accountingSourceId: string
        importedFrom: string | null
        importedTo: string | null
        intervals: { importedFrom: string; importedTo: string }[]
    }[]
}

export function getAccountingDre(companyId: string, startDate: string, endDate: string) {
    const params = new URLSearchParams({ startDate, endDate })

    return apiRequest<AccountingDre>(`${companyPath(companyId)}/dre?${params}`)
}
