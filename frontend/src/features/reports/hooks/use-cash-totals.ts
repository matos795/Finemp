import {
    keepPreviousData,
    useQueries,
    useQuery,
} from "@tanstack/react-query"
import {
    getDailyCashTotals,
    getMonthlyCashTotals,
    listMonthlyCashCategoryTotals,
    listMonthlyCashTitles,
} from "../api/reports-api"
import type {
    CashActivity,
    CashDirection,
    CashState,
    MonthlyCashCategoryTotal,
    MonthlyCashDetailFilter,
} from "../types/cash-totals.types"

export function useMonthlyCashTotals(
    companyId: string | undefined,
    year: number,
) {
    return useQuery({
        queryKey: ["companies", companyId, "financial-reports", "monthly-cash-totals", year],
        queryFn: () => getMonthlyCashTotals(companyId!, year),
        enabled: Boolean(companyId) && Number.isInteger(year),
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId
                ? keepPreviousData(previousData)
                : undefined,
    })
}

export function useDailyCashTotals(
    companyId: string | undefined,
    month: string,
) {
    return useQuery({
        queryKey: ["companies", companyId, "financial-reports", "daily-cash-totals", month],
        queryFn: () => getDailyCashTotals(companyId!, month),
        enabled: Boolean(companyId) && /^\d{4}-\d{2}$/.test(month),
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId
                ? keepPreviousData(previousData)
                : undefined,
    })
}

/** Máximo aceito pelo endpoint por página. */
export const CATEGORY_PAGE_SIZE = 100

async function listAllMonthlyCashCategoryTotals(
    companyId: string,
    filter: {
        month: string
        direction: CashDirection
        state: CashState
        activity: CashActivity
    },
): Promise<MonthlyCashCategoryTotal[]> {
    const first = await listMonthlyCashCategoryTotals(
        companyId,
        filter,
        0,
        CATEGORY_PAGE_SIZE,
    )

    const items = [...first.items]

    for (let page = 1; page < first.totalPages; page += 1) {
        const next = await listMonthlyCashCategoryTotals(
            companyId,
            filter,
            page,
            CATEGORY_PAGE_SIZE,
        )

        items.push(...next.items)
    }

    return items
}

/**
 * Subtotais por categoria dos 12 meses de uma linha do fluxo mensal
 * (uma consulta por mês, só quando a linha é aberta).
 */
export function useMonthlyCashCategoryTotals(
    companyId: string | undefined,
    year: number,
    direction: CashDirection,
    state: CashState,
    activity: CashActivity,
    enabled: boolean,
) {
    return useQueries({
        queries: Array.from({ length: 12 }, (_, index) => {
            const month = `${String(year).padStart(4, "0")}-${String(index + 1).padStart(2, "0")}`

            return {
                queryKey: [
                    "companies",
                    companyId,
                    "financial-reports",
                    "monthly-cash-by-category",
                    { month, direction, state, activity },
                ],
                queryFn: () =>
                    listAllMonthlyCashCategoryTotals(
                        companyId!,
                        { month, direction, state, activity },
                    ),
                enabled: enabled && Boolean(companyId),
            }
        }),
    })
}

/** Títulos que compõem uma célula do fluxo mensal, paginados no servidor. */
export function useMonthlyCashTitles(
    companyId: string | undefined,
    filter: MonthlyCashDetailFilter | null,
    page: number,
    size: number,
) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "financial-reports",
            "monthly-cash-titles",
            { ...filter, page, size },
        ],
        queryFn: () =>
            listMonthlyCashTitles(companyId!, filter!, page, size),
        enabled: Boolean(companyId && filter),
        placeholderData: keepPreviousData,
    })
}
