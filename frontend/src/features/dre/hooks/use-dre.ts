import { useQuery } from "@tanstack/react-query"
import type { DrePeriod } from "../types/dre.types"
import { getDre } from "../api/dre-api"


export function useDre(
    companyId: string | undefined,
    period: DrePeriod,
) {
    return useQuery({
        queryKey: [
            "dre",
            companyId,
            period.startDate,
            period.endDate,
        ],

        queryFn: () =>
            getDre(
                companyId!,
                period,
            ),

        enabled: Boolean(companyId),
    })
}