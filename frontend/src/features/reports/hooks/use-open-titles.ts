import {
    keepPreviousData,
    useQueries,
    useQuery,
} from "@tanstack/react-query"
import {
    listOpenTitlePartnerTotals,
    listOpenTitles,
} from "../api/reports-api"
import type {
    OpenTitlePartnerTotal,
    OpenTitlesFilter,
} from "../types/aging.types"

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

/** Títulos de cada grupo da página atual (uma consulta por grupo). */
export function useOpenTitlesOfGroups(
    companyId: string | undefined,
    filter: OpenTitlesFilter,
    groups: OpenTitlePartnerTotal[],
) {
    return useQueries({
        queries: groups.map((group) => ({
            queryKey: [
                "companies",
                companyId,
                "financial-reports",
                "open-titles",
                {
                    ...filter,
                    partnerId: group.partnerId,
                    page: 0,
                    size: GROUP_TITLES_SIZE,
                },
            ],
            queryFn: () =>
                listOpenTitles(
                    companyId!,
                    filter,
                    0,
                    GROUP_TITLES_SIZE,
                    group.partnerId
                        ? { partnerId: group.partnerId }
                        : { unassignedPartner: true as const },
                ),
            enabled: Boolean(companyId && filter.asOf),
        })),
    })
}
