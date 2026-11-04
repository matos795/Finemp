import { apiRequest } from "../../../lib/api/api-client"
import type { PageRequest } from "../../../lib/api/pagination.types"
import type {
    StatementItemFilters,
    StatementItemPage,
} from "../types/statement.types"

export function bankAccountStatementPath(
    companyId: string,
    bankId: string,
) {
    return `/api/companies/${encodeURIComponent(companyId)}/bank-accounts/${encodeURIComponent(bankId)}`
}

/**
 * `GET …/bank-accounts/{bankId}/statement-items` (listStatementItems).
 * Filtros, ordem (data decrescente) e paginação ficam no servidor.
 */
export async function listStatementItems(
    companyId: string,
    bankId: string,
    filters: StatementItemFilters,
    pagination: PageRequest,
): Promise<StatementItemPage> {
    const params = new URLSearchParams({
        page: String(pagination.page),
        size: String(pagination.size),
    })

    if (filters.status) {
        params.set("status", filters.status)
    }

    return apiRequest<StatementItemPage>(
        `${bankAccountStatementPath(companyId, bankId)}/statement-items?${params}`,
    )
}
