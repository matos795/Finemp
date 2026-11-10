import { apiRequest } from "../../../lib/api/api-client.ts"
import {
    WITHOUT_CENTER,
    type FinancialEntryCommandReceipt,
    type FinancialEntryCommandValues,
    type FinancialEntryFilter,
    type FinancialEntryPage,
} from "../types/financial-entry.types.ts"

export const FINANCIAL_ENTRIES_PAGE_SIZE = 50

function financialEntriesPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/financial-entries`
}

/**
 * Parâmetros de `listFinancialEntries`. Só envia filtros preenchidos;
 * "Sem centro" vira `withoutCenter=true` (o contrato proíbe combiná-lo
 * com `centerId`).
 */
export function financialEntryQuery(
    filter: FinancialEntryFilter,
    page: number,
    size = FINANCIAL_ENTRIES_PAGE_SIZE,
) {
    const params = new URLSearchParams({
        page: String(page),
        size: String(size),
    })

    const optional: [string, string][] = [
        ["from", filter.from],
        ["to", filter.to],
        ["direction", filter.direction],
        ["status", filter.status],
        ["bankId", filter.bankId],
        ["categoryId", filter.categoryId],
        ["search", filter.search.trim()],
    ]

    for (const [name, value] of optional) {
        if (value) {
            params.set(name, value)
        }
    }

    if (filter.center === WITHOUT_CENTER) {
        params.set("withoutCenter", "true")
    } else if (filter.center) {
        params.set("centerId", filter.center)
    }

    return params
}

/**
 * `GET …/financial-entries` (listFinancialEntries). Filtros, paginação,
 * ordem, valores e situação ficam no servidor.
 */
export function listFinancialEntries(
    companyId: string,
    filter: FinancialEntryFilter,
    page: number,
    signal?: AbortSignal,
): Promise<FinancialEntryPage> {
    return apiRequest<FinancialEntryPage>(
        `${financialEntriesPath(companyId)}?${financialEntryQuery(filter, page)}`,
        { signal },
    )
}

/** `POST …/financial-entries` (createFinancialEntry), com Idempotency-Key. */
export function createFinancialEntry(
    companyId: string,
    values: FinancialEntryCommandValues,
    idempotencyKey: string,
): Promise<FinancialEntryCommandReceipt> {
    return apiRequest<FinancialEntryCommandReceipt>(
        financialEntriesPath(companyId),
        {
            method: "POST",
            headers: { "Idempotency-Key": idempotencyKey },
            body: JSON.stringify({ values }),
        },
    )
}

/**
 * `PUT …/financial-entries/{entryId}` (updateFinancialEntry): substituição
 * completa dos valores com a versão lida (409 STALE_VERSION se mudou).
 */
export function updateFinancialEntry(
    companyId: string,
    entryId: string,
    values: FinancialEntryCommandValues,
    expectedVersion: number,
    idempotencyKey: string,
): Promise<FinancialEntryCommandReceipt> {
    return apiRequest<FinancialEntryCommandReceipt>(
        `${financialEntriesPath(companyId)}/${encodeURIComponent(entryId)}`,
        {
            method: "PUT",
            headers: { "Idempotency-Key": idempotencyKey },
            body: JSON.stringify({ values, expectedVersion }),
        },
    )
}
