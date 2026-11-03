import {
    keepPreviousData,
    useQuery,
} from "@tanstack/react-query"
import {
    getDailyCashTotals,
    getMonthlyCashTotals,
} from "../api/reports-api"

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
