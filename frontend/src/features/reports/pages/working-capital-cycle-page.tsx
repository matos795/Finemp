import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency, formatIsoDate } from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getWorkingCapitalCycle } from "../api/reports-api"
import type {
    WorkingCapitalCycleSide,
    WorkingCapitalPartnerTermMetrics,
    WorkingCapitalTermBand,
} from "../types/working-capital-cycle.types"

const bandLabel: Record<WorkingCapitalTermBand, string> = {
    CASH: "À vista",
    DAYS_1_30: "1 a 30 dias",
    DAYS_31_60: "31 a 60 dias",
    DAYS_61_90: "61 a 90 dias",
    OVER_90: "Acima de 90 dias",
}

function days(value: string | null) {
    return value === null ? "—" : `${value} dias`
}

function percent(value: string | null) {
    return value === null ? "—" : `${value}%`
}

function money(value: string | null) {
    return value === null ? "—" : formatExactCurrency(value)
}

export function WorkingCapitalCyclePage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const [months, setMonths] = useState<1 | 3 | 6 | 12>(3)
    const [pmeInput, setPmeInput] = useState("0")

    const pmeDays = /^\d+$/.test(pmeInput) ? Number(pmeInput) : Number.NaN
    const validPme = Number.isSafeInteger(pmeDays) && pmeDays >= 0

    const report = useQuery({
        queryKey: ["working-capital-cycle", companyId, months, pmeDays] as const,
        queryFn: () => getWorkingCapitalCycle(companyId!, months, pmeDays),
        enabled: Boolean(companyId && validPme),
        retry: false,
    })

    const data = report.isError ? undefined : report.data

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="cycleMonths">Período</label>
                    <select
                        id="cycleMonths"
                        value={months}
                        onChange={(event) =>
                            setMonths(Number(event.target.value) as 1 | 3 | 6 | 12)
                        }
                    >
                        <option value={1}>Mês atual</option>
                        <option value={3}>3 meses</option>
                        <option value={6}>6 meses</option>
                        <option value={12}>12 meses</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="cyclePme">PME (dias)</label>
                    <input
                        id="cyclePme"
                        type="number"
                        min="0"
                        step="1"
                        value={pmeInput}
                        onChange={(event) => setPmeInput(event.target.value)}
                    />
                </div>
            </div>

            <div className="panel">
                <div className="ph">
                    <div>
                        <h2>Ciclo financeiro e prazos</h2>
                        <p className="help">
                            PMR e PMP usam os prazos reais quando existem pagamentos e,
                            na ausência deles, os prazos concedidos. O PME informado vale
                            somente para esta análise e não altera cadastros.
                        </p>
                    </div>
                    {data ? (
                        <span className="badge b-neu">
                            {formatIsoDate(data.from)} a {formatIsoDate(data.to)}
                        </span>
                    ) : null}
                </div>
            </div>

            {!validPme ? (
                <div className="alert e">Informe o PME como número inteiro maior ou igual a zero.</div>
            ) : report.isError ? (
                <QueryError
                    error={report.error}
                    messages={{
                        400: "Confira o período e o PME informados.",
                        403: "Você não tem acesso aos dados financeiros desta empresa.",
                    }}
                    fallback="Não foi possível carregar o ciclo financeiro."
                    onRetry={() => report.refetch()}
                />
            ) : report.isPending ? (
                <div className="empty">Carregando ciclo financeiro…</div>
            ) : data ? (
                <>
                    <div className="cards">
                        <MetricCard
                            label="PMR"
                            value={days(data.pmrDays)}
                            sub={`Concedido ${days(data.receivables.grantedDays)} · Real ${days(data.receivables.realDays)} · Saldo ${days(data.receivables.balanceDays)}`}
                        />
                        <MetricCard
                            label="PMP"
                            value={days(data.pmpDays)}
                            sub={`Concedido ${days(data.payables.grantedDays)} · Real ${days(data.payables.realDays)} · Saldo ${days(data.payables.balanceDays)}`}
                        />
                        <MetricCard
                            label="PME"
                            value={`${data.pmeDays} dias`}
                            sub={data.pmeDays === 0 ? "Sem prazo de estoque informado" : "Informado nesta consulta"}
                        />
                        <MetricCard
                            label="Ciclo financeiro"
                            value={days(data.financialCycleDays)}
                            sub={`Ciclo operacional ${days(data.operatingCycleDays)}`}
                        />
                        <MetricCard
                            label="Capital de giro para o ciclo"
                            value={money(data.workingCapitalForCycle)}
                            sub={`Vendas por dia ${money(data.receivables.dailyVolume)}`}
                        />
                        <MetricCard
                            label="Inadimplência"
                            value={percent(data.receivables.overduePercent)}
                            sub={`Vencido ${money(data.receivables.overdueAmount)} · Em aberto ${money(data.receivables.openAmount)}`}
                        />
                        <MetricCard
                            label="Pontualidade dos clientes"
                            value={percent(data.receivables.punctualityPercent)}
                            sub={`Juros recebidos ${money(data.receivables.interest)}`}
                        />
                        <MetricCard
                            label="Pontualidade nos pagamentos"
                            value={percent(data.payables.punctualityPercent)}
                            sub={`Juros pagos ${money(data.payables.interest)}`}
                        />
                    </div>

                    <div className="panel">
                        <div className="ph">
                            <div>
                                <h2>Prazos concedidos no período</h2>
                                <p className="help">
                                    {data.daysInPeriod} dia(s) considerados na base diária.
                                </p>
                            </div>
                        </div>
                        <TermBands
                            receivables={data.receivables}
                            payables={data.payables}
                        />
                    </div>

                    <div className="panel">
                        <div className="ph">
                            <div>
                                <h2>Prazos por parceiro</h2>
                                <p className="help">
                                    Top 10 por volume emitido somado ao valor vencido em aberto.
                                </p>
                            </div>
                        </div>
                        <PartnerTable
                            title="Clientes"
                            rows={data.receivables.partners}
                        />
                        <PartnerTable
                            title="Fornecedores"
                            rows={data.payables.partners}
                        />
                    </div>

                    <div className="panel">
                        <div className="ph">
                            <div>
                                <h2>Como os prazos são calculados</h2>
                                <p className="help">
                                    Prazo concedido considera documento até vencimento.
                                    Prazo real considera documento até pagamento e é ponderado
                                    pelo valor efetivamente pago. Títulos em aberto e vencidos
                                    refletem a posição atual da empresa.
                                </p>
                            </div>
                        </div>
                    </div>
                </>
            ) : null}
        </>
    )
}

function MetricCard({
    label,
    value,
    sub,
}: {
    label: string
    value: string
    sub: string
}) {
    return (
        <div className="card">
            <div className="lb">{label}</div>
            <div className="vl">{value}</div>
            <div className="sub">{sub}</div>
        </div>
    )
}

function TermBands({
    receivables,
    payables,
}: {
    receivables: WorkingCapitalCycleSide
    payables: WorkingCapitalCycleSide
}) {
    return (
        <div className="tblwrap">
            <table>
                <thead>
                    <tr>
                        <th>Faixa</th>
                        <th className="n">Vendas</th>
                        <th className="n">Valor vendas</th>
                        <th className="n">Compras</th>
                        <th className="n">Valor compras</th>
                    </tr>
                </thead>
                <tbody>
                    {receivables.bands.map((receivableBand, index) => {
                        const payableBand = payables.bands[index]
                        return (
                            <tr key={receivableBand.band}>
                                <td>{bandLabel[receivableBand.band]}</td>
                                <td className="n">{receivableBand.count}</td>
                                <td className="n">{formatExactCurrency(receivableBand.amount)}</td>
                                <td className="n">{payableBand?.count ?? 0}</td>
                                <td className="n">
                                    {payableBand
                                        ? formatExactCurrency(payableBand.amount)
                                        : formatExactCurrency("0.00")}
                                </td>
                            </tr>
                        )
                    })}
                </tbody>
            </table>
        </div>
    )
}

function PartnerTable({
    title,
    rows,
}: {
    title: string
    rows: WorkingCapitalPartnerTermMetrics[]
}) {
    return (
        <>
            <h3>{title}</h3>
            <div className="tblwrap">
                <table>
                    <thead>
                        <tr>
                            <th>Parceiro</th>
                            <th className="n">Volume</th>
                            <th className="n">Concedido</th>
                            <th className="n">Real</th>
                            <th className="n">Atraso médio</th>
                            <th className="n">Vencido em aberto</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map((row) => (
                            <tr key={row.partnerId ?? "unassigned"}>
                                <td>{row.partnerName}</td>
                                <td className="n">{formatExactCurrency(row.volume)}</td>
                                <td className="n">{days(row.grantedDays)}</td>
                                <td className="n">{days(row.realDays)}</td>
                                <td className="n">{days(row.averageDelayDays)}</td>
                                <td className="n">{formatExactCurrency(row.overdueOpenAmount)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {rows.length === 0 ? (
                    <div className="empty">Nenhum parceiro com movimento nesta base.</div>
                ) : null}
            </div>
        </>
    )
}
