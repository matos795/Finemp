import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency } from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getCashVsOperationalDre } from "../api/reports-api"
import { formatCompetence } from "../lib/calendar-labels"

export function CashVsOperationalDrePage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const [month, setMonth] = useState(() => todayIso().slice(0, 7))

    const report = useQuery({
        queryKey: ["cash-vs-operational-dre", companyId, month] as const,
        queryFn: () => getCashVsOperationalDre(companyId!, month),
        enabled: Boolean(companyId) && /^\d{4}-\d{2}$/.test(month),
        retry: false,
    })

    const data = report.data
    const monthLabel = formatCompetence(data?.month ?? month)

    return (
        <>
            <Panel
                title="Conciliação caixa × DRE"
                help="Comparação mensal entre o caixa realizado e a DRE operacional por competência. Os cálculos vêm do backend."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="cashVsDreMonth">Mês de análise</label>
                        <input
                            id="cashVsDreMonth"
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

            <div className="alert i">
                Visão parcial: compara lançamentos financeiros do FINEMP. Não é
                uma conciliação contábil completa com a DRE do Questor, e a
                diferença não recebe causa automática.
            </div>

            {report.isError ? (
                <QueryError
                    error={report.error}
                    messages={{
                        400: "Confira o mês de análise.",
                        403: "Você não tem acesso aos números financeiros desta empresa.",
                        404: "A comparação caixa × DRE não está disponível neste ambiente.",
                    }}
                    fallback="Não foi possível carregar a comparação caixa × DRE."
                    onRetry={() => report.refetch()}
                />
            ) : null}

            {report.isPending && report.fetchStatus !== "idle" ? (
                <div className="empty">Carregando…</div>
            ) : null}

            {data ? (
                <div style={report.isPlaceholderData ? { opacity: 0.6 } : undefined}>
                    <div className="cards">
                        <div className="card">
                            <div className="lb">Geração de caixa · {monthLabel}</div>
                            <div className="vl">
                                {formatExactCurrency(data.cash.netCashGeneration)}
                            </div>
                            <div className="sub">
                                Entradas menos saídas efetivamente realizadas
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">Resultado DRE · {monthLabel}</div>
                            <div className="vl">
                                {formatExactCurrency(data.operationalDre.netResult)}
                            </div>
                            <div className="sub">
                                DRE operacional por competência
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">Diferença caixa × competência</div>
                            <div className="vl">
                                {formatExactCurrency(data.difference)}
                            </div>
                            <div className="sub">
                                Sem atribuição automática de causa
                            </div>
                        </div>
                    </div>

                    <Panel
                        title="Composição da comparação"
                        help="Valores do mesmo mês, calculados e conciliados temporalmente apenas até o limite da cobertura disponível."
                    >
                        <div className="tblwrap">
                            <table>
                                <tbody>
                                    <tr>
                                        <td>Entradas realizadas</td>
                                        <td className="n">
                                            <b>{formatExactCurrency(data.cash.realizedIncome)}</b>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>Saídas realizadas</td>
                                        <td className="n">
                                            <b>{formatExactCurrency(data.cash.realizedExpense)}</b>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>Geração líquida de caixa</td>
                                        <td className="n">
                                            <b>{formatExactCurrency(data.cash.netCashGeneration)}</b>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>Resultado líquido da DRE operacional</td>
                                        <td className="n">
                                            <b>{formatExactCurrency(data.operationalDre.netResult)}</b>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>Diferença caixa × competência</td>
                                        <td className="n">
                                            <b>{formatExactCurrency(data.difference)}</b>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </Panel>

                    <p className="help">
                        Cobertura: {data.coverage.join(", ")} · relatório
                        {data.partial ? " parcial" : " completo"}.
                    </p>
                </div>
            ) : null}
        </>
    )
}
