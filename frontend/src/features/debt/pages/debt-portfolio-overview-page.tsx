import { useQuery } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency } from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getDebtPortfolioOverview } from "../api/debts-api"
import type { DebtOrigin } from "../types/debt.types"

const originLabel: Record<DebtOrigin, string> = {
    BANK: "Bancos",
    SUPPLIER: "Fornecedores",
    TAX: "Impostos",
    THIRD_PARTY: "Terceiros",
}

export function DebtPortfolioOverviewPage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id

    const overview = useQuery({
        queryKey: ["debt-portfolio-overview", companyId] as const,
        queryFn: ({ signal }) => getDebtPortfolioOverview(companyId!, signal),
        enabled: Boolean(companyId),
        retry: false,
    })

    const data = overview.isError ? undefined : overview.data

    if (overview.isError) {
        return (
            <QueryError
                error={overview.error}
                messages={{
                    403: "Você não tem acesso ao endividamento desta empresa.",
                }}
                fallback="Não foi possível carregar a visão geral do endividamento."
                onRetry={() => overview.refetch()}
            />
        )
    }

    if (overview.isPending) {
        return <div className="empty">Carregando…</div>
    }

    if (!data) return null

    return (
        <>
            <Panel
                title="Visão geral do endividamento"
                help="Leitura contratual dos acordos cadastrados. Não representa baixas, pagamentos realizados nem saldo efetivamente em aberto."
            >
                <div className="alert i">
                    Os valores abaixo vêm dos contratos e cronogramas calculados.
                    Pagamentos ainda não são conciliados com este módulo.
                </div>
            </Panel>

            <div className="cards">
                <div className="card">
                    <div className="lb">Acordos cadastrados</div>
                    <div className="vl">{data.agreementCount}</div>
                </div>

                <div className="card">
                    <div className="lb">Principal contratado</div>
                    <div className="vl">
                        {formatExactCurrency(data.principalAmount)}
                    </div>
                </div>

                <div className="card">
                    <div className="lb">Parcelas contratuais</div>
                    <div className="vl">
                        {formatExactCurrency(data.scheduledAmount)}
                    </div>
                    <div className="sub">Soma do cronograma completo</div>
                </div>

                <div className="card">
                    <div className="lb">Juros contratuais</div>
                    <div className="vl">
                        {formatExactCurrency(data.interestAmount)}
                    </div>
                    <div className="sub">Soma prevista no cronograma</div>
                </div>
            </div>

            <Panel
                title="Composição por origem"
                help="Os quatro grupos usam os mesmos acordos já cadastrados nas telas de Bancos, Fornecedores, Impostos e Terceiros."
            >
                <div className="tblwrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Origem</th>
                                <th className="n">Acordos</th>
                                <th className="n">Principal</th>
                                <th className="n">Parcelas contratuais</th>
                                <th className="n">Juros contratuais</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.byOrigin.map((row) => (
                                <tr key={row.origin}>
                                    <td>{originLabel[row.origin]}</td>
                                    <td className="n">{row.agreementCount}</td>
                                    <td className="n">
                                        {formatExactCurrency(row.principalAmount)}
                                    </td>
                                    <td className="n">
                                        {formatExactCurrency(row.scheduledAmount)}
                                    </td>
                                    <td className="n">
                                        {formatExactCurrency(row.interestAmount)}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </Panel>
        </>
    )
}
