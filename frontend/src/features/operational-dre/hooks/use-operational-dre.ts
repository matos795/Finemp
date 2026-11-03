import {
    keepPreviousData,
    useQueries,
    useQuery,
} from "@tanstack/react-query"
import {
    getOperationalDre,
    getOperationalDreEffects,
    type EffectsQuery,
} from "../../../lib/api/presentation-client"

export function monthOf(year: number, monthIndex: number) {
    return `${String(year).padStart(4, "0")}-${String(monthIndex + 1).padStart(2, "0")}`
}

/** DRE operacional dos 12 meses do ano (uma consulta explícita por mês). */
export function useOperationalDreYear(
    companyId: string | undefined,
    year: number,
) {
    return useQueries({
        queries: Array.from({ length: 12 }, (_, index) => {
            const month = monthOf(year, index)

            return {
                queryKey: ["companies", companyId, "financial-reports", "operational-dre", month],
                queryFn: ({ signal }: { signal: AbortSignal }) =>
                    getOperationalDre(companyId!, month, signal),
                enabled: Boolean(companyId),
            }
        }),
    })
}

/** Efeitos que compõem uma célula da DRE operacional, paginados no servidor. */
export function useOperationalDreEffects(
    companyId: string | undefined,
    query: EffectsQuery,
) {
    return useQuery({
        queryKey: ["companies", companyId, "financial-reports", "operational-dre-effects", query],
        queryFn: ({ signal }) =>
            getOperationalDreEffects(companyId!, query, signal),
        enabled: Boolean(companyId),
        placeholderData: keepPreviousData,
    })
}
