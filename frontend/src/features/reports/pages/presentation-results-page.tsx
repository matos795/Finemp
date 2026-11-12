import { useEffect, useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency, formatIsoDate } from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useDashboardSnapshot } from "../../dashboard/hooks/use-dashboard-snapshot"
import {
    canSeeFinancialDashboard,
    dashboardMonthCash,
    dashboardQuery,
    describeDashboardError,
    formatIndicatorPercent,
} from "../../dashboard/lib/financial-dashboard"
import { formatCompetence } from "../lib/calendar-labels"

export function PresentationResultsPage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <PresentationResultsCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function PresentationResultsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const queryClient = useQueryClient()

    const today = todayIso()
    const [month, setMonth] = useState(today.slice(0, 7))
    const query = dashboardQuery(today, month)
    const canSee = canSeeFinancialDashboard(hasPermission)
    const snapshot = useDashboardSnapshot(companyId, query, canSee)

    const sessionExpired =
        snapshot.isError && apiErrorStatus(snapshot.error) === 401

    useEffect(() => {
        if (sessionExpired) {
            queryClient.setQueryData(currentUserQueryKey, null)
        }
    }, [queryClient, sessionExpired])

    if (!canSee) {
        return (
            <div className="alert i">
                Seu acesso não inclui os números financeiros desta empresa.
            </div>
        )
    }

    const data = snapshot.isError ? undefined : snapshot.data
    const cash = data ? dashboardMonthCash(data) : null
    const dre = data?.operationalDre
    const indicators = data?.indicators
    const dreIndicators = indicators?.dre
    const monthLabel = formatCompetence(data?.month ?? month)

    return (
        <>
            <Panel
                title="Apresentação de resultados"
                help="Síntese executiva mensal com caixa realizado, títulos em aberto e DRE operacional parcial. Os cálculos vêm do backend."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="presentationMonth">Mês de análise</label>
                        <input
                            id="presentationMonth"
                            type="month"
                            value={month}
                            onChange={(event) => {
                                if (/^\d{4}-\d{2}$/.test(event.target.value)) {
                                    setMonth(event.target.value)
                                }
                            }}
                        />
                    </div>
                </div>
            </Panel>

            {snapshot.isError && !sessionExpired ? (
                <QueryError
                    error={snapshot.error}
                    messages={{
                        400: "Confira o mês de análise.",
                        403: "Você não tem acesso aos números financeiros desta empresa.",
                        404: "A apresentação de resultados não está disponível neste ambiente.",
                    }}
                    fallback={describeDashboardError(snapshot.error)}
                    onRetry={() => snapshot.refetch()}
                />
            ) : null}

            {snapshot.isPending && snapshot.fetchStatus !== "idle" ? (
                <div className="empty">Carregando…</div>
            ) : null}

            {data ? (
                <div style={snapshot.isPlaceholderData ? { opacity: 0.6 } : undefined}>
                    <div className="cards">
                        <div className="card">
                            <div className="lb">Entradas realizadas · {monthLabel}</div>
                            <div className="vl pos">
                                {cash ? formatExactCurrency(cash.realIncome) : "—"}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">Saídas realizadas · {monthLabel}</div>
                            <div className="vl neg">
                                {cash ? formatExactCurrency(cash.realExpense) : "—"}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">Geração de caixa</div>
                            <div className="vl">
                                {formatExactCurrency(indicators.cash.generation)}
                            </div>
                            <div className="sub">
                                Margem {formatIndicatorPercent(indicators.cash.margin) ?? "—"}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">A receber em aberto</div>
                            <div className="vl">
                                {formatExactCurrency(data.receivables.totalAmount)}
                            </div>
                            <div className="sub">
                                {data.receivables.totalCount} título(s) · vencidos{" "}
                                {formatExactCurrency(data.receivables.overdueAmount)}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">A pagar em aberto</div>
                            <div className="vl">
                                {formatExactCurrency(data.payables.totalAmount)}
                            </div>
                            <div className="sub">
                                {data.payables.totalCount} título(s) · vencidos{" "}
                                {formatExactCurrency(data.payables.overdueAmount)}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">Inadimplência</div>
                            <div className="vl">
                                {formatIndicatorPercent(indicators.receivables.overdueRate) ?? "—"}
                            </div>
                        </div>
                    </div>

                    {dre ? (
                        <Panel
                            title="DRE operacional"
                            help="Cobertura parcial: lançamentos financeiros registrados no FINEMP."
                        >
                            <div className="tblwrap">
                                <table>
                                    <tbody>
                                        <DreRow label="Receita líquida" value={dre.subtotals.netRevenue} />
                                        <DreRow label="Lucro bruto" value={dre.subtotals.grossProfit} percent={formatIndicatorPercent(dreIndicators?.grossMargin)} />
                                        <DreRow label="EBITDA" value={dre.subtotals.ebitda} percent={formatIndicatorPercent(dreIndicators?.ebitdaMargin)} />
                                        <DreRow label="EBIT" value={dre.subtotals.ebit} />
                                        <DreRow label="Resultado antes dos tributos" value={dre.subtotals.profitBeforeTax} />
                                        <DreRow label="Resultado líquido" value={dre.subtotals.netResult} percent={formatIndicatorPercent(dreIndicators?.netMargin)} />
                                    </tbody>
                                </table>
                            </div>
                        </Panel>
                    ) : (
                        <div className="alert i">
                            A DRE operacional não está disponível neste snapshot.
                        </div>
                    )}

                    <p className="help">
                        Posição de títulos em {formatIsoDate(data.asOf)}. Relatório parcial:
                        considera apenas a cobertura financeira disponível no FINEMP.
                    </p>
                </div>
            ) : null}
        </>
    )
}

function DreRow({
    label,
    value,
    percent,
}: {
    label: string
    value: string
    percent?: string | null
}) {
    return (
        <tr>
            <td>{label}</td>
            <td className="n"><b>{formatExactCurrency(value)}</b></td>
            <td className="n">{percent ?? ""}</td>
        </tr>
    )
}
