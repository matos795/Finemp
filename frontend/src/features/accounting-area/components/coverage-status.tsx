import { useQuery } from "@tanstack/react-query"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { getDatasetCoverage } from "../api/accounting-data-api"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { describeCoverage, formatDay, formatInstant, type AccountingDataset } from "../lib/accounting-v2"
import { accountingQueryKey } from "../lib/query-keys"

const DATASET_LABELS: Record<AccountingDataset, string> = {
    CHART: "Plano de contas",
    MOVEMENTS: "Movimentos",
    DAILY_TOTALS: "Totais diários de débito e crédito",
    MONTHLY_TOTALS: "Totais mensais de débito e crédito",
    ORIGIN_CATALOG: "Catálogo técnico de origem",
    PARTICIPANTS: "Participantes",
    COST_ALLOCATIONS: "Rateios",
}

interface CoverageStatusProps {
    companyId: string
    accountingSourceId: string
    dataset: AccountingDataset
    establishmentCode?: string
}

/**
 * Cobertura importada de um dataset (GET /accounting-dataset-coverage, #509):
 * intervalos realmente importados com as lacunas, ou o instante do snapshot.
 * Significa "já importado ao menos uma vez", nunca uma data-limite da base.
 */
export function CoverageStatus({ companyId, accountingSourceId, dataset, establishmentCode }: CoverageStatusProps) {
    const userId = useSessionUserId()
    const coverage = useQuery({
        queryKey: accountingQueryKey(userId, "coverage", companyId, accountingSourceId, dataset, establishmentCode ?? ""),
        queryFn: () => getDatasetCoverage(companyId, { accountingSourceId, dataset, establishmentCode }),
        retry: false,
    })
    const label = DATASET_LABELS[dataset]

    let content

    if (coverage.isLoading) {
        content = <span className="muted">Carregando a cobertura importada…</span>
    } else if (coverage.isError) {
        content = (
            <span className="muted">
                {apiErrorStatus(coverage.error) === 403
                    ? "Sem acesso à cobertura deste dado."
                    : "Não foi possível carregar a cobertura importada."}
            </span>
        )
    } else if (coverage.data) {
        const view = describeCoverage(coverage.data)

        if (view.state === "none") {
            content = (
                <>
                    <span className="tag pend">Nada importado</span>{" "}
                    <span className="muted">Nenhum dado de {label.toLowerCase()} importado para esta fonte ainda.</span>
                </>
            )
        } else if (view.state === "snapshot") {
            content = (
                <>
                    <span className="tag ok">Publicado</span>{" "}
                    <span>
                        Dados importados: versão publicada em <b className="mono">{formatInstant(view.publishedAt)}</b>.
                    </span>
                </>
            )
        } else {
            content = (
                <>
                    <span className="tag ok">Importado</span>{" "}
                    <span>
                        Dados importados:{" "}
                        {view.intervals.map((interval, index) => (
                            <span key={interval.from} className="mono">
                                {index > 0 && " · "}
                                {formatDay(interval.from)} a {formatDay(interval.to)}
                            </span>
                        ))}
                    </span>
                    {view.gaps.length > 0 && (
                        <>
                            {" "}
                            <span className="tag pend">Lacunas</span>{" "}
                            <span className="muted">
                                Não importado:{" "}
                                {view.gaps.map((gap, index) => (
                                    <span key={gap.from} className="mono">
                                        {index > 0 && " · "}
                                        {formatDay(gap.from)} a {formatDay(gap.to)}
                                    </span>
                                ))}
                            </span>
                        </>
                    )}
                </>
            )
        }
    }

    return (
        <div className="ctb-cov" role="status" aria-label={`Cobertura importada · ${label}`}>
            <span className="ctb-cov-l">Cobertura importada · {label}</span>
            <span className="ctb-cov-v">{content}</span>
        </div>
    )
}
