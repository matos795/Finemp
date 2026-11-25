import { useQueries } from "@tanstack/react-query"
import { getAccountingChart } from "../api/accounting-data-api"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import type { AccountingSourceOption } from "../hooks/use-accounting-sources"
import { formatInstant } from "../lib/accounting-v2"
import { accountingQueryKey } from "../lib/query-keys"

interface ChartUniverseNoticeProps {
    companyId: string
    sources: AccountingSourceOption[]
    onOpenChart?: () => void
}

/**
 * De-Para: diz de onde vem o universo de contas. A lista do De-Para é sempre
 * /accounting-accounts (contas conhecidas pelo FINEMP); com o plano completo
 * publicado (FULL_CHART) ela já inclui as contas do plano, senão só as observadas.
 */
export function ChartUniverseNotice({ companyId, sources, onOpenChart }: ChartUniverseNoticeProps) {
    const userId = useSessionUserId()
    const charts = useQueries({
        queries: sources.map((source) => ({
            queryKey: accountingQueryKey(userId, "chart", companyId, source.id, "availability"),
            queryFn: () => getAccountingChart(companyId, { accountingSourceId: source.id, search: "", page: 0, size: 1 }),
            retry: false,
        })),
    })

    if (sources.length === 0 || charts.some((chart) => chart.isLoading)) {
        return null
    }

    return (
        <div className="ctb-cov" role="status" aria-label="Universo de contas">
            <span className="ctb-cov-l">Universo de contas</span>
            <span className="ctb-cov-v">
                {charts.map((chart, index) => {
                    const source = sources[index]
                    const prefix = sources.length > 1 ? `${source.label}: ` : ""

                    if (chart.isError || !chart.data) {
                        return (
                            <span key={source.id} className="muted">
                                {prefix}não foi possível saber se o plano completo foi publicado.{" "}
                            </span>
                        )
                    }

                    if (chart.data.availability === "FULL_CHART") {
                        return (
                            <span key={source.id}>
                                {prefix}
                                <span className="tag ok">Plano completo</span> publicado em{" "}
                                <b className="mono">{formatInstant(chart.data.coverage.publishedAt ?? "")}</b>: a lista
                                inclui as contas do plano e as observadas nos movimentos.{" "}
                            </span>
                        )
                    }

                    return (
                        <span key={source.id}>
                            {prefix}
                            <span className="tag pend">Só contas importadas</span> plano completo ainda não publicado:
                            a lista mostra só as contas importadas/observadas nos movimentos (não é o plano completo).{" "}
                        </span>
                    )
                })}
                {onOpenChart && (
                    <button type="button" className="lnk" onClick={onOpenChart}>
                        Ver Plano de Contas
                    </button>
                )}
            </span>
        </div>
    )
}
