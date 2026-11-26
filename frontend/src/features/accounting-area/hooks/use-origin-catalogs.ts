import { useQueries } from "@tanstack/react-query"
import { getOriginCatalog } from "../api/accounting-data-api"
import { accountingQueryKey } from "../lib/query-keys"
import { useSessionUserId } from "./use-accounting-area-queries"

/**
 * Catálogo técnico de origem (#509) por fonte, só quando publicado. As fontes
 * vêm dos próprios movimentos exibidos: não depende da descoberta de fontes.
 */
export function useOriginCatalogs(companyId: string | null, sourceIds: readonly string[], enabled: boolean) {
    const userId = useSessionUserId()
    const unique = [...new Set(sourceIds)]
    const results = useQueries({
        queries: unique.map((sourceId) => ({
            queryKey: accountingQueryKey(userId, "origin-catalog", companyId, sourceId),
            queryFn: () => getOriginCatalog(companyId as string, sourceId),
            enabled: enabled && companyId !== null,
            retry: false,
        })),
    })

    const bySource = new Map<string, Map<string, string>>()

    results.forEach((result, index) => {
        if (result.data?.availability === "AVAILABLE") {
            bySource.set(unique[index], new Map(result.data.items.map((entry) => [entry.originCode, entry.description])))
        }
    })

    return bySource
}
