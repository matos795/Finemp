import { useEffect, useState } from "react"
import { useNavigate } from "react-router"
import { useQueryClient } from "@tanstack/react-query"
import { formatExactCurrency, formatIsoDate, isNegativeExactDecimal } from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useCompanyAccess } from "../../companies/company-access-context"
import { formatCompetence } from "../../reports/lib/calendar-labels"
import { useDashboardSnapshot } from "../hooks/use-dashboard-snapshot"
import {
    canSeeFinancialDashboard,
    dashboardMonthCash,
    dashboardQuery,
    describeDashboardError,
    formatIndicatorPercent,
    overdueSummary,
} from "../lib/financial-dashboard"

/**
 * Fluxo de caixa › Painel (renderPainel do protótipo). Mostra só o que o
 * backend já calcula no presentation-snapshot: entradas e saídas do mês
 * (realizadas e em aberto), geração e margem de caixa, títulos a receber
 * e a pagar com vencidos, inadimplência e o resultado da DRE operacional
 * parcial quando habilitada. Saldo, projeção, autonomia, comparação com o
 * mês anterior e gráficos ficam fora até existirem na API.
 */
export function DashboardPage() {
    const { selectedCompany } = useCompanyAccess()

    // O estado da tela (mês escolhido) pertence à Company ativa.
    return <DashboardCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function DashboardCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const navigate = useNavigate()
    const queryClient = useQueryClient()

    const today = todayIso()
    const [month, setMonth] = useState(today.slice(0, 7))
    const query = dashboardQuery(today, month)

    const canSee = canSeeFinancialDashboard(hasPermission)
    const snapshot = useDashboardSnapshot(companyId, query, canSee)
    const sessionExpired = snapshot.isError && apiErrorStatus(snapshot.error) === 401

    useEffect(() => {
        if (sessionExpired) {
            queryClient.setQueryData(currentUserQueryKey, null)
        }
    }, [sessionExpired, queryClient])

    if (!canSee) {
        return (
            <div className="alert i">
                Seu acesso não inclui os números financeiros desta empresa. Peça ao
                administrador para liberar a visualização dos lançamentos financeiros.
            </div>
        )
    }

    const data = snapshot.isError ? undefined : snapshot.data
    const cash = data ? dashboardMonthCash(data) : null
    const overdue = data ? overdueSummary(data) : null
    const monthLabel = formatCompetence(month)
    const generation = data?.indicators.cash.generation
    const netResult = data?.operationalDre?.subtotals.netResult
    const cashMargin = formatIndicatorPercent(data?.indicators.cash.margin)
    const overdueRate = formatIndicatorPercent(data?.indicators.receivables.overdueRate)

    function openReport(path: string) {
        navigate(path)
    }

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="pMes">Mês de análise</label>
                    <input
                        type="month"
                        id="pMes"
                        value={month}
                        onChange={(event) => {
                            if (/^\d{4}-\d{2}$/.test(event.target.value)) {
                                setMonth(event.target.value)
                            }
                        }}
                    />
                </div>
            </div>

            {snapshot.isError && !sessionExpired ? (
                <div className="alert e">
                    {describeDashboardError(snapshot.error)}{" "}
                    <button
                        type="button"
                        className="btn sec sm"
                        onClick={() => snapshot.refetch()}
                    >
                        Tentar novamente
                    </button>
                </div>
            ) : null}

            {snapshot.isPending && snapshot.fetchStatus !== "idle" ? (
                <div className="empty">Carregando…</div>
            ) : null}

            {data ? (
                <div style={snapshot.isPlaceholderData ? { opacity: 0.6 } : undefined}>
                    {overdue ? (
                        <div className="alert e">
                            <b>Atenção:</b>{" "}
                            {overdue.payables
                                ? `${overdue.payables.count} conta(s) a pagar vencida(s) (${formatExactCurrency(overdue.payables.amount)})`
                                : ""}
                            {overdue.payables && overdue.receivables ? " e " : ""}
                            {overdue.receivables
                                ? `${overdue.receivables.count} recebimento(s) atrasado(s) (${formatExactCurrency(overdue.receivables.amount)})`
                                : ""}
                            .
                        </div>
                    ) : null}

                    <div className="cards">
                        <div className="card">
                            <div className="lb">Entradas de {monthLabel}</div>
                            <div className="vl pos">{cash ? formatExactCurrency(cash.realIncome) : "—"}</div>
                            <div className="sub">
                                {cash ? `+ ${formatExactCurrency(cash.openIncome)} a receber no mês` : ""}
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">Saídas de {monthLabel}</div>
                            <div className="vl neg">{cash ? formatExactCurrency(cash.realExpense) : "—"}</div>
                            <div className="sub">
                                {cash ? `+ ${formatExactCurrency(cash.openExpense)} a pagar no mês` : ""}
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">Geração de caixa do mês</div>
                            <div className={generation && isNegativeExactDecimal(generation) ? "vl neg" : "vl pos"}>
                                {generation ? formatExactCurrency(generation) : "—"}
                            </div>
                            <div className="sub">
                                Entradas menos saídas realizadas
                                {netResult !== undefined ? (
                                    <>
                                        <br />
                                        Resultado DRE (competência, parcial): <b>{formatExactCurrency(netResult)}</b>
                                    </>
                                ) : null}
                            </div>
                        </div>

                        <div
                            className={data.receivables.overdueCount > 0 ? "card click alerta" : "card click"}
                            style={{ borderLeft: "4px solid var(--ok)" }}
                            role="link"
                            tabIndex={0}
                            onClick={() => openReport("/reports/receivables")}
                            onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                    openReport("/reports/receivables")
                                }
                            }}
                        >
                            <div className="lb">A receber (em aberto)</div>
                            <div className="vl">{formatExactCurrency(data.receivables.totalAmount)}</div>
                            <div className="sub">
                                {data.receivables.totalCount} título(s) · vencidos {formatExactCurrency(data.receivables.overdueAmount)}
                            </div>
                        </div>

                        <div
                            className={data.payables.overdueCount > 0 ? "card click alerta" : "card click"}
                            style={{ borderLeft: "4px solid var(--bad)" }}
                            role="link"
                            tabIndex={0}
                            onClick={() => openReport("/reports/payables")}
                            onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                    openReport("/reports/payables")
                                }
                            }}
                        >
                            <div className="lb">A pagar (em aberto)</div>
                            <div className="vl">{formatExactCurrency(data.payables.totalAmount)}</div>
                            <div className="sub">
                                {data.payables.totalCount} título(s) · vencidos {formatExactCurrency(data.payables.overdueAmount)}
                            </div>
                        </div>
                    </div>

                    <div className="panel">
                        <h2>Indicadores</h2>
                        <ul className="lst">
                            <li>
                                <span>
                                    Margem de caixa do mês
                                    <div className="d">Geração de caixa ÷ entradas</div>
                                </span>
                                <b title={cashMargin ? undefined : "Indisponível: não há entradas realizadas no mês"}>{cashMargin ?? "—"}</b>
                            </li>
                            <li>
                                <span>
                                    Inadimplência
                                    <div className="d">A receber vencido ÷ total a receber</div>
                                </span>
                                <b title={overdueRate ? undefined : "Indisponível: não há valores a receber"}>{overdueRate ?? "—"}</b>
                            </li>
                        </ul>
                    </div>

                    <p className="help">
                        Valores parciais: consideram os lançamentos financeiros registrados
                        no FINEMP. Títulos em aberto na posição de {formatIsoDate(data.asOf)}.
                    </p>
                </div>
            ) : null}
        </>
    )
}
