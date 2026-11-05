import { useState } from "react"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useDailyCashTotals } from "../hooks/use-cash-totals"
import { CashAmountCell } from "../components/cash-amount-cell"
import { dayLabel } from "../lib/calendar-labels"

const errorMessages = {
    400: "Confira o mês.",
    403: "Você não tem acesso ao fluxo de caixa desta empresa.",
    404: "O fluxo diário não está disponível neste ambiente.",
}

/**
 * Fluxo de caixa › Fluxo diário (parcial): por dia, recebido, pago, a
 * receber e a pagar, como o backend devolve. Movimento do dia, saldo
 * acumulado, gráfico e resumo do mês dependem de saldo e não são
 * calculados aqui (PA4).
 */
export function DailyCashFlowPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const today = todayIso()
    const [month, setMonth] = useState(today.slice(0, 7))

    const totals =
        useDailyCashTotals(selectedCompany?.id, month)

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="fdMes">Mês</label>
                    <input
                        type="month"
                        id="fdMes"
                        value={month}
                        onChange={(event) => setMonth(event.target.value || today.slice(0, 7))}
                    />
                </div>
            </div>

            <div className="alert i">
                Fluxo diário parcial: recebido, pago, a receber e a pagar por dia.
                Movimento do dia, saldo acumulado, gráfico e resumo do mês serão
                exibidos quando o saldo estiver disponível na API.
            </div>

            <Panel>
                {totals.isError ? (
                    <QueryError
                        error={totals.error}
                        messages={errorMessages}
                        fallback="Não foi possível carregar o fluxo diário."
                        onRetry={() => totals.refetch()}
                    />
                ) : (
                    <div
                        className="mtx"
                        style={totals.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Dia</th>
                                    <th className="n">Recebido</th>
                                    <th className="n">Pago</th>
                                    <th className="n">A receber</th>
                                    <th className="n">A pagar</th>
                                </tr>
                            </thead>

                            <tbody>
                                {totals.isPending && (
                                    <tr>
                                        <td
                                            colSpan={5}
                                            className="empty"
                                        >
                                            Carregando…
                                        </td>
                                    </tr>
                                )}

                                {totals.data?.days.map((day) => (
                                    <tr
                                        key={day.date}
                                        className={day.date === today ? "hoje" : undefined}
                                    >
                                        <td>
                                            {dayLabel(day.date)}
                                            {day.date === today && (
                                                <>
                                                    {" "}
                                                    <span className="badge b-gold">hoje</span>
                                                </>
                                            )}
                                        </td>
                                        <CashAmountCell
                                            value={day.realIncome}
                                            tone="pos"
                                        />
                                        <CashAmountCell
                                            value={day.realExpense}
                                            tone="neg"
                                        />
                                        <CashAmountCell value={day.openIncome} />
                                        <CashAmountCell value={day.openExpense} />
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </Panel>
        </>
    )
}
