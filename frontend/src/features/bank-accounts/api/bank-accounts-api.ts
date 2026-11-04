import { apiRequest } from "../../../lib/api/api-client"
import type { PageRequest } from "../../../lib/api/pagination.types"
import type {
    BankAccount,
    BankAccountDeletion,
    BankAccountPage,
    BankAccountWrite,
} from "../types/bank-account.types"

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
        `${bankAccountsPath(companyId)}?${params}`,
    )
}

function bankAccountsPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/bank-accounts`
}

/** `POST …/bank-accounts` (createFinancialBankAccount). */
export async function createBankAccount(
    companyId: string,
    values: BankAccountWrite,
): Promise<BankAccount> {
    return apiRequest<BankAccount>(bankAccountsPath(companyId), {
        method: "POST",
        body: JSON.stringify(values),
    })
}

/** `PUT …/bank-accounts/{bankId}` com a versão atual (409 STALE_VERSION). */
export async function updateBankAccount(
    companyId: string,
    bankId: string,
    values: BankAccountWrite,
    expectedVersion: number,
): Promise<BankAccount> {
    return apiRequest<BankAccount>(
        `${bankAccountsPath(companyId)}/${encodeURIComponent(bankId)}`,
        {
            method: "PUT",
            body: JSON.stringify({
                ...values,
                expectedVersion,
            }),
        },
    )
}

/**
 * `DELETE …/bank-accounts/{bankId}?version=`. O backend decide:
 * sem uso, remove (DELETED); com uso, só desativa (DEACTIVATED).
 */
export async function deleteBankAccount(
    companyId: string,
    bankId: string,
    version: number,
): Promise<BankAccountDeletion> {
    const params = new URLSearchParams({
        version: String(version),
    })

    return apiRequest<BankAccountDeletion>(
        `${bankAccountsPath(companyId)}/${encodeURIComponent(bankId)}?${params}`,
        {
            method: "DELETE",
        },
    )
}
