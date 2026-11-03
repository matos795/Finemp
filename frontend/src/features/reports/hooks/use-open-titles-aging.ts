import {
    keepPreviousData,
    useQuery,
} from "@tanstack/react-query"
import { getOpenTitlesAging } from "../api/reports-api"
import type {
    AgingDirection,
    AgingScope,
} from "../types/aging.types"

export function useOpenTitlesAging(
    companyId: string | undefined,
    direction: AgingDirection,
    asOf: string,
    scope: AgingScope,
) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "financial-reports",
            "aging",
            { direction, asOf, scope },
        ],
        queryFn: () =>
            getOpenTitlesAging(companyId!, direction, asOf, scope),
        enabled: Boolean(companyId && asOf),
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId
                ? keepPreviousData(previousData)
                : undefined,
    })
}
