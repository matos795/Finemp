import { useState } from "react"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useMonthlyCashTotals } from "../hooks/use-cash-totals"
import { CashAmountCell } from "../components/cash-amount-cell"
import { monthLabels } from "../lib/calendar-labels"
import type { CashTotals } from "../types/cash-totals.types"

const rows: {
    key: keyof CashTotals
    label: string
    className: "grp" | "sub"
}[] = [
    { key: "realIncome", label: "Recebimentos realizados", className: "grp" },
    { key: "realExpense", label: "(–) Pagamentos realizados", className: "grp" },
    { key: "openIncome", label: "A receber em aberto (pelo vencimento)", className: "sub" },
    { key: "openExpense", label: "A pagar em aberto (pelo vencimento)", className: "sub" },
]

const errorMessages = {
    400: "Confira o ano.",
    403: "Você não tem acesso ao fluxo de caixa desta empresa.",
    404: "O fluxo mensal não está disponível neste ambiente.",
}

/**
 * Fluxo de caixa › Fluxo mensal (parcial). A API entrega, por mês, as
 * entradas e saídas realizadas (pela baixa) e em aberto (pelo
 * vencimento). Saldos, categorias, atividades, transferências, totais,
 * médias e projeção ainda não vêm da API e não são calculados aqui (PA4).
 */
export function MonthlyCashFlowPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const currentYear = new Date().getFullYear()
    const [year, setYear] = useState(currentYear)

    const totals =
        useMonthlyCashTotals(selectedCompany?.id, year)

    const years = Array.from({ length: 6 }, (_, index) => currentYear + 1 - index)

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="fmAno">Ano</label>
                    <select
                        id="fmAno"
                        value={year}
                        onChange={(event) => setYear(Number(event.target.value))}
                    >
                        {years.map((option) => (
                            <option
                                key={option}
                                value={option}
                            >
                                {option}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            <div className="alert i">
                Fluxo mensal parcial: entradas e saídas realizadas e em aberto.
                Saldo inicial e final, categorias, fluxo de investimento e de
                financiamento, transferências, totais e projeção serão exibidos
                quando estiverem disponíveis na API.
            </div>

            <Panel
                title={`Fluxo de caixa ${year}`}
                help="Realizado: pela data e pelo valor efetivo da baixa. Em aberto: pelo vencimento e pelo valor nominal."
            >
                {totals.isError ? (
                    <QueryError
                        error={totals.error}
                        messages={errorMessages}
                        fallback="Não foi possível carregar o fluxo mensal."
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
                                    <th>Fluxo de caixa</th>
                                    {monthLabels.map((label) => (
                                        <th
                                            key={label}
                                            className="n"
                                        >
                                            {label}
                                        </th>
                                    ))}
                                </tr>
                            </thead>

                            <tbody>
                                {rows.map((row) => (
                                    <tr
                                        key={row.key}
                                        className={row.className}
                                    >
                                        <td>{row.label}</td>
                                        {monthLabels.map((label, index) => (
                                            <CashAmountCell
                                                key={label}
                                                value={
                                                    totals.data?.months.find(
                                                        (month) => month.month === index + 1,
                                                    )?.[row.key]
                                                }
                                            />
                                        ))}
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
