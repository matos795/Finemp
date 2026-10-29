import { apiRequest } from "../../../lib/api/api-client"
import type { PageRequest } from "../../../lib/api/pagination.types"
import type { BankAccountPage } from "../types/bank-account.types"

/**
 * `GET /api/companies/{companyId}/bank-accounts` (listFinancialBankAccounts).
 * Paginação, ordem (nome e UUID) e autorização ficam no servidor.
 */
export async function listBankAccounts(
    companyId: string,
    pagination: PageRequest,
): Promise<BankAccountPage> {
    const params = new URLSearchParams({
        page: String(pagination.page),
        size: String(pagination.size),
    })

    return apiRequest<BankAccountPage>(
        `/api/companies/${encodeURIComponent(companyId)}/bank-accounts?${params}`,
    )
}
