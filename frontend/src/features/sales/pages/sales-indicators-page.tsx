import { useQuery } from "@tanstack/react-query"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency } from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getSalesIndicators } from "../api/sales-api"

function percent(value: string) {
    return `${value.replace(".", ",")}%`
}

export function SalesIndicatorsPage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id

    const report = useQuery({
        queryKey: ["sales-indicators", companyId] as const,
        queryFn: () => getSalesIndicators(companyId!),
        enabled: Boolean(companyId),
        retry: false,
    })

    const data = report.isError ? undefined : report.data

    if (report.isError) {
        return (
            <QueryError
                error={report.error}
                messages={{
                    403: "Você não tem acesso aos indicadores comerciais desta empresa.",
                }}
                fallback="Não foi possível carregar os indicadores comerciais."
                onRetry={() => report.refetch()}
            />
        )
    }

    if (report.isPending) {
        return <div className="empty">Carregando…</div>
    }

    if (!data) return null

    return (
        <>
            <div className="cards">
                <div className="card">
                    <div className="lb">Pipeline ponderado</div>
                    <div className="vl">
                        {formatExactCurrency(data.weightedOpenPipeline)}
                    </div>
                    <div className="sub">Valor mensal × probabilidade</div>
                </div>

                <div className="card">
                    <div className="lb">Conversão</div>
                    <div className="vl">{percent(data.conversionRate)}</div>
                    <div className="sub">
                        {data.wonCount} ganha(s) · {data.lostCount} perdida(s)
                    </div>
                </div>

                <div className="card">
                    <div className="lb">Ticket médio ganho</div>
                    <div className="vl">
                        {formatExactCurrency(data.averageWonTicket)}
                    </div>
                    <div className="sub">Valor mensal médio</div>
                </div>

                <div className="card">
                    <div className="lb">Ciclo comercial médio</div>
                    <div className="vl">
                        {data.averageSalesCycleDays.replace(".", ",")} dias
                    </div>
                    <div className="sub">Criação → ganho</div>
                </div>

                <div className="card">
                    <div className="lb">Valor anualizado ganho</div>
                    <div className="vl">
                        {formatExactCurrency(data.annualizedWonValue)}
                    </div>
                    <div className="sub">Valores mensais ganhos × 12</div>
                </div>
            </div>

            <div className="panel" style={{ padding: 0 }}>
                <div className="ph" style={{ padding: 16 }}>
                    <div>
                        <h2>Pipeline previsto</h2>
                        <p className="help">
                            Oportunidades atrasadas são trazidas para o mês atual.
                        </p>
                    </div>
                </div>
                <div className="tblwrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Mês previsto</th>
                                <th className="n">Valor ponderado</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.forecast.map((month) => (
                                <tr key={month.month}>
                                    <td>{month.month}</td>
                                    <td className="n">
                                        {formatExactCurrency(month.weightedAmount)}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                {data.forecast.length === 0 ? (
                    <div className="empty">Nenhuma oportunidade aberta no pipeline.</div>
                ) : null}
            </div>
        </>
    )
}
