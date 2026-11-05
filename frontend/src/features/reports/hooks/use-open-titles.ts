import {
    keepPreviousData,
    useInfiniteQuery,
    useQuery,
} from "@tanstack/react-query"
import {
    listOpenTitlePartnerTotals,
    listOpenTitles,
} from "../api/reports-api"
import type { OpenTitlesFilter } from "../types/aging.types"

/** Títulos de um grupo carregados de uma vez (máximo do contrato). */
export const GROUP_TITLES_SIZE = 100

function keepForSameCompany(companyId: string | undefined) {
    return <T,>(
        previousData: T | undefined,
        previousQuery: { queryKey: readonly unknown[] } | undefined,
    ) =>
        previousQuery?.queryKey[1] === companyId
            ? keepPreviousData(previousData)
            : undefined
}

/** Lista por vencimento (Agrupar = Não), paginada no servidor. */
export function useOpenTitlesList(
    companyId: string | undefined,
    filter: OpenTitlesFilter,
    page: number,
    size: number,
    enabled: boolean,
) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "financial-reports",
            "open-titles",
            { ...filter, page, size },
        ],
        queryFn: () =>
            listOpenTitles(companyId!, filter, page, size),
        enabled: enabled && Boolean(companyId && filter.asOf),
        placeholderData: keepForSameCompany(companyId),
    })
}

/** Grupos por cliente/fornecedor (Agrupar = Sim), paginados no servidor. */
export function useOpenTitlePartnerTotals(
    companyId: string | undefined,
    filter: OpenTitlesFilter,
    page: number,
    size: number,
    enabled: boolean,
) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "financial-reports",
            "open-titles-by-partner",
            { ...filter, page, size },
        ],
        queryFn: () =>
            listOpenTitlePartnerTotals(companyId!, filter, page, size),
        enabled: enabled && Boolean(companyId && filter.asOf),
        placeholderData: keepForSameCompany(companyId),
    })
}

/** Títulos de um parceiro no modo agrupado, com paginação incremental. */
export function useOpenTitlesOfPartner(
    companyId: string | undefined,
    filter: OpenTitlesFilter,
    partnerId: string | null,
) {
    return useInfiniteQuery({
        queryKey: [
            "companies",
            companyId,
            "financial-reports",
            "open-titles",
            {
                ...filter,
                partnerId,
                pageSize: GROUP_TITLES_SIZE,
            },
        ],
        initialPageParam: 0,
        queryFn: ({ pageParam }) =>
            listOpenTitles(
                companyId!,
                filter,
                pageParam,
                GROUP_TITLES_SIZE,
                partnerId
                    ? { partnerId }
                    : { unassignedPartner: true as const },
            ),
        getNextPageParam: (lastPage) => {
            const nextPage = lastPage.page + 1

            return nextPage < lastPage.totalPages
                ? nextPage
                : undefined
        },
        enabled: Boolean(companyId && filter.asOf),
    })
}
