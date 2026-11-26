import { useQueries } from "@tanstack/react-query"
import { getAccountingChart, type AccountingChartPage } from "../api/accounting-data-api"
import { accountingQueryKey } from "../lib/query-keys"
import { useSessionUserId } from "./use-accounting-area-queries"

const PAGE_SIZE = 100
/** Limite de leitura do plano para o De-Para (5.000 contas por fonte). */
const MAX_PAGES = 50

type ChartItem = AccountingChartPage["items"][number]

async function readWholeChart(companyId: string, accountingSourceId: string) {
    const first = await getAccountingChart(companyId, { accountingSourceId, search: "", page: 0, size: PAGE_SIZE })

    if (first.availability !== "FULL_CHART") {
        return { availability: first.availability, publishedAt: null, items: [] as ChartItem[], complete: true }
    }

    const items = [...first.items]
    const pages = Math.min(first.totalPages, MAX_PAGES)

    for (let page = 1; page < pages; page++) {
        const next = await getAccountingChart(companyId, { accountingSourceId, search: "", page, size: PAGE_SIZE })
        items.push(...next.items)
    }

    return {
        availability: first.availability,
        publishedAt: first.coverage.publishedAt,
        items,
        complete: first.totalPages <= MAX_PAGES,
    }
}

/**
 * Plano completo publicado (/accounting-chart) por fonte, para o De-Para dizer
 * se cada conta está no plano ou só foi observada. Lê só fontes com FULL_CHART;
 * sem plano publicado, nenhuma conta é marcada como "no plano".
 */
export function useChartMembership(companyId: string | null, sourceIds: readonly string[], enabled: boolean) {
    const userId = useSessionUserId()
    const results = useQueries({
        queries: sourceIds.map((sourceId) => ({
            queryKey: accountingQueryKey(userId, "chart", companyId, sourceId, "membership"),
            queryFn: () => readWholeChart(companyId as string, sourceId),
            enabled: enabled && companyId !== null,
            staleTime: 60_000,
            retry: false,
        })),
    })

    const accounts = new Map<string, ChartItem>()
    const fullChartSources = new Set<string>()
    let complete = true

    results.forEach((result, index) => {
        if (result.data?.availability === "FULL_CHART") {
            fullChartSources.add(sourceIds[index])
            complete = complete && result.data.complete

            for (const item of result.data.items) {
                accounts.set(item.accountId, item)
            }
        }
    })

    return {
        loading: results.some((result) => result.isLoading),
        failed: results.some((result) => result.isError),
        accounts,
        fullChartSources,
        complete,
    }
}
