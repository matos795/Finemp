import { useQuery } from "@tanstack/react-query"
import type { PageRequest } from "../../../lib/api/pagination.types"
import { listBankAccounts } from "../api/bank-accounts-api"

export function bankAccountsQueryKey(
    companyId: string | undefined,
    pagination: PageRequest,
) {
    return [
        "companies",
        companyId,
        "bank-accounts",
        pagination,
    ] as const
}

export function useBankAccounts(
    companyId: string | undefined,
    pagination: PageRequest,
) {
    return useQuery({
        queryKey: bankAccountsQueryKey(
            companyId,
            pagination,
        ),

        queryFn: () =>
            listBankAccounts(
                companyId!,
                pagination,
            ),

        enabled: Boolean(companyId),

        // Mantém a página anterior só durante a troca de página
        // da mesma empresa; nunca mostra dados de outra empresa.
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId
                ? previousData
                : undefined,
    })
}
