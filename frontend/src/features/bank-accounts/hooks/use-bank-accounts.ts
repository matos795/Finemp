import { useQuery } from "@tanstack/react-query"
import type { PageRequest } from "../../../lib/api/pagination.types"
import { listBankAccounts } from "../api/bank-accounts-api"

const MAX_PAGE_SIZE = 100

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

/**
 * Lista completa de contas da empresa para seletores que precisam permitir
 * escolher qualquer conta. A API continua paginada; o frontend percorre
 * todas as páginas do contrato atual sem inventar endpoint de busca.
 */
export function useAllBankAccounts(
    companyId: string | undefined,
) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "bank-accounts",
            "all",
        ],

        queryFn: async () => {
            const firstPage = await listBankAccounts(
                companyId!,
                { page: 0, size: MAX_PAGE_SIZE },
            )

            if (firstPage.totalPages <= 1) {
                return firstPage.items
            }

            const remainingPages = await Promise.all(
                Array.from(
                    { length: firstPage.totalPages - 1 },
                    (_, index) =>
                        listBankAccounts(
                            companyId!,
                            {
                                page: index + 1,
                                size: MAX_PAGE_SIZE,
                            },
                        ),
                ),
            )

            return [
                ...firstPage.items,
                ...remainingPages.flatMap((page) => page.items),
            ]
        },

        enabled: Boolean(companyId),
    })
}
