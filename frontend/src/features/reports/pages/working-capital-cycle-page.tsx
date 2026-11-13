import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactCurrency,
    formatExactDecimal,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getWorkingCapitalCycle } from "../api/reports-api"

const decimalPattern = /^\d+(?:[.,]\d{1,2})?$/

export function WorkingCapitalCyclePage() {
    const { selectedCompany } = useCompanyAccess()
    return (
        <CompanyWorkingCapitalCycle
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function CompanyWorkingCapitalCycle() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const today = todayIso()
    const [from, setFrom] = useState(`${today.slice(0, 7)}-01`)
    const [to, setTo] = useState(today)
    const [inventoryDays, setInventoryDays] = useState("")

    const inventoryValid =
        !inventoryDays.trim() || decimalPattern.test(inventoryDays.trim())
    const periodValid = Boolean(from && to && from <= to)

    const report = useQuery({
        queryKey: [
            "working-capital-cycle",
            companyId,
            from,
            to,
            inventoryDays.trim().replace(",", "."),
        ] as const,
        queryFn: () =>
            getWorkingCapitalCycle(
                companyId!,
                from,
                to,
                inventoryDays.trim() || undefined,
            ),
        enabled: Boolean(companyId && periodValid && inventoryValid),
        retry: false,
    })

    const data = report.isError ? undefined : report.data

    return (
        <>
            <Panel
                title="Ciclo financeiro e prazos"
                help="PMR e PMP são médias ponderadas pelo valor dos títulos. Títulos pagos usam o prazo real; títulos ainda abertos usam o prazo concedido até o vencimento. Os cálculos são feitos no backend."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="cycleFrom">De</label>
                        <input
                            id="cycleFrom"
                            type="date"
                            value={from}
                            onChange={(event) => setFrom(event.target.value)}
                        />
                    </div>

                    <div className="f">
                        <label htmlFor="cycleTo">Até</label>
                        <input
                            id="cycleTo"
                            type="date"
                            value={to}
                            onChange={(event) => setTo(event.target.value)}
                        />
                    </div>

                    <div className="f">
                        <label htmlFor="cycleInventoryDays">PME manual (dias)</label>
                        <input
                            id="cycleInventoryDays"
                            inputMode="decimal"
                            maxLength={7}
                            placeholder="Opcional"
                            value={inventoryDays}
                            onChange={(event) => setInventoryDays(event.target.value)}
                        />
                    </div>
                </div>

                {!periodValid ? (
                    <div className="alert e">Confira o período informado.</div>
                ) : null}

                {!inventoryValid ? (
                    <div className="alert e">
                        Informe o PME com até duas casas decimais.
                    </div>
                ) : null}

                <p className="help">
                    O PME ainda não é lido do módulo de Estoque. Nesta entrega ele é
                    apenas um parâmetro da consulta e não é salvo.
                </p>
            </Panel>

            {report.isError ? (
                <QueryError
                    error={report.error}
                    messages={{
                        400: "Confira o período e o PME informado.",
                        403: "Você não tem acesso aos dados financeiros desta empresa.",
                    }}
                    fallback="Não foi possível calcular o ciclo financeiro."
                    onRetry={() => report.refetch()}
                />
            ) : null}

            {report.isPending && report.fetchStatus !== "idle" ? (
                <div className="empty">Calculando…</div>
            ) : null}

            {data ? (
                <>
                    <div
                        className="cards"
                        style={report.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <MetricCard
                            label="PMR"
                            value={formatDays(data.receivablesDays)}
                            sub={`${data.receivableTitles} título(s) de receita`}
                        />
                        <MetricCard
                            label="PMP"
                            value={formatDays(data.payablesDays)}
                            sub={`${data.payableTitles} título(s) de despesa`}
                        />
                        <MetricCard
                            label="PME"
                            value={formatDays(data.inventoryDays)}
                            sub="Parâmetro manual desta consulta"
                        />
                        <MetricCard
                            label="Ciclo operacional"
                            value={formatDays(data.operatingCycleDays)}
                            sub="PME + PMR"
                        />
                        <MetricCard
                            label="Ciclo financeiro"
                            value={formatDays(data.financialCycleDays)}
                            sub="PME + PMR − PMP"
                        />
                        <MetricCard
                            label="NCG estimada pelo ciclo"
                            value={
                                data.cycleWorkingCapital
                                    ? formatExactCurrency(data.cycleWorkingCapital)
                                    : "—"
                            }
                            sub="Receita diária × ciclo financeiro"
                        />
                    </div>

                    <Panel
                        title="Base do cálculo"
                        help="Esta visão não substitui o relatório completo de Capital de giro (NCG/Fleuriet), que permanece separado até suas dependências estarem implementadas."
                    >
                        <div className="tblwrap">
                            <table>
                                <tbody>
                                    <tr>
                                        <td>Período</td>
                                        <td className="n">
                                            {formatIsoDate(data.from)} a {formatIsoDate(data.to)}
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>Dias no período</td>
                                        <td className="n">{data.periodDays}</td>
                                    </tr>
                                    <tr>
                                        <td>Receita diária média</td>
                                        <td className="n">
                                            {formatExactCurrency(data.dailyRevenue)}
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </Panel>
                </>
            ) : null}
        </>
    )
}

function formatDays(value: string | null) {
    return value === null ? "—" : `${formatExactDecimal(value)} dias`
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
