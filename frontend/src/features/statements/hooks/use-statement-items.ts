import {
    keepPreviousData,
    useQueries,
    useQuery,
} from "@tanstack/react-query"
import type { PageRequest } from "../../../lib/api/pagination.types"
import { listStatementItems } from "../api/statements-api"
import type {
    StatementItemFilters,
    StatementItemStatus,
} from "../types/statement.types"

export function statementItemsQueryKey(
    companyId: string | undefined,
    bankId: string | undefined,
) {
    return [
        "companies",
        companyId,
        "bank-accounts",
        bankId,
        "statement-items",
    ] as const
}

export function useStatementItems(
    companyId: string | undefined,
    bankId: string | undefined,
    filters: StatementItemFilters,
    pagination: PageRequest,
) {
    return useQuery({
        queryKey: [
            ...statementItemsQueryKey(companyId, bankId),
            filters,
            pagination,
        ],
        queryFn: () =>
            listStatementItems(companyId!, bankId!, filters, pagination),
        enabled: Boolean(companyId && bankId),
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId &&
            previousQuery?.queryKey[3] === bankId
                ? keepPreviousData(previousData)
                : undefined,
    })
}

const COUNTED_STATUSES: (StatementItemStatus | undefined)[] = [
    "PENDING",
    "RECONCILED",
    "IGNORED",
    undefined,
]

/**
 * Quantidades das pílulas (Pendentes, Conciliados, Ignorados, Todos):
 * o totalItems que o servidor informa para cada filtro, sem contar no
 * frontend. Cada consulta pede só um item.
 */
export function useStatementItemCounts(
    companyId: string | undefined,
    bankId: string | undefined,
) {
    return useQueries({
        queries: COUNTED_STATUSES.map((status) => ({
            queryKey: [
                ...statementItemsQueryKey(companyId, bankId),
                { status },
                { page: 0, size: 1 },
            ],
            queryFn: () =>
                listStatementItems(companyId!, bankId!, { status }, { page: 0, size: 1 }),
            enabled: Boolean(companyId && bankId),
        })),
        combine: (results) => ({
            PENDING: results[0].data?.totalItems,
            RECONCILED: results[1].data?.totalItems,
            IGNORED: results[2].data?.totalItems,
            ALL: results[3].data?.totalItems,
        }),
    })
}
