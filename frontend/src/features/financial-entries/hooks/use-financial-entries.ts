import { useQuery } from "@tanstack/react-query"
import { listFinancialEntries } from "../api/financial-entries-api"
import type { FinancialEntryFilter } from "../types/financial-entry.types"

export function financialEntriesQueryKey(
    companyId: string | undefined,
    filter: FinancialEntryFilter,
    page: number,
) {
    return ["companies", companyId, "financial-entries", { filter, page }] as const
}

/**
 * Página de lançamentos da empresa selecionada. A requisição anterior é
 * cancelada ao trocar empresa, filtro ou página.
 */
export function useFinancialEntries(
    companyId: string | undefined,
    filter: FinancialEntryFilter,
    page: number,
) {
    return useQuery({
        queryKey: financialEntriesQueryKey(companyId, filter, page),
        queryFn: ({ signal }) =>
            listFinancialEntries(companyId!, filter, page, signal),
        enabled: Boolean(companyId),
        // Só reaproveita a página anterior da mesma empresa.
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId
                ? previousData
                : undefined,
    })
}
