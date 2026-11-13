import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactCurrency,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getMonthlyContractBillingPreview } from "../api/contracts-api"

export function MonthlyContractBillingPage() {
    const { selectedCompany } = useCompanyAccess()
    return (
        <MonthlyContractBillingCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function MonthlyContractBillingCompanyPage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const [month, setMonth] = useState(todayIso().slice(0, 7))
    const [page, setPage] = useCompanyPage(companyId, month)

    const preview = useQuery({
        queryKey: ["monthly-contract-billing", companyId, month, page] as const,
        queryFn: ({ signal }) =>
            getMonthlyContractBillingPreview(companyId!, month, page, signal),
        enabled: Boolean(companyId && /^\d{4}-\d{2}$/.test(month)),
        placeholderData: (previous) => previous,
        retry: false,
    })

    const data = preview.isError ? undefined : preview.data
    const items = data?.contracts.items ?? []

    useEffect(() => {
        const totalPages = data?.contracts.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [data?.contracts.totalPages, page, setPage])

    return (
        <>
            <Panel
                title="Faturamento do mês"
                help="Prévia contratual read-only: considera apenas contratos ativos e vigentes na competência. Não gera cobrança nem nota fiscal."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="billingMonth">Competência</label>
                        <input
                            id="billingMonth"
                            type="month"
                            value={month}
                            onChange={(event) => {
                                setMonth(event.target.value)
                                setPage(0)
                            }}
                        />
                    </div>
                </div>

                <p className="help">
                    Adicionais, 13º, reajustes históricos e notas fiscais ainda não entram
                    nesta prévia.
                </p>
            </Panel>

            {preview.isError ? (
                <QueryError
                    error={preview.error}
                    messages={{
                        400: "Confira a competência informada.",
                        403: "Você não tem acesso aos contratos desta empresa.",
                    }}
                    fallback="Não foi possível carregar a prévia de faturamento."
                    onRetry={() => preview.refetch()}
                />
            ) : null}

            {data ? (
                <div style={preview.isPlaceholderData ? { opacity: 0.6 } : undefined}>
                    <div className="cards">
                        <div className="card">
                            <div className="lb">Receitas contratuais</div>
                            <div className="vl pos">
                                {formatExactCurrency(data.incomeTotal)}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">Despesas contratuais</div>
                            <div className="vl neg">
                                {formatExactCurrency(data.expenseTotal)}
                            </div>
                        </div>
                        <div className="card">
                            <div className="lb">Saldo contratual</div>
                            <div className="vl">
                                {formatExactCurrency(data.netTotal)}
                            </div>
                        </div>
                    </div>

                    <Panel
                        title="Contratos da competência"
                        help="A data de cobrança respeita o dia do contrato e é limitada ao último dia do mês."
                    >
                        <div className="tblwrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Parceiro</th>
                                        <th>Contrato</th>
                                        <th>Tipo</th>
                                        <th>Data de cobrança</th>
                                        <th className="n">Valor</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((line) => (
                                        <tr key={line.contractId}>
                                            <td>{line.businessPartnerName}</td>
                                            <td>{line.description}</td>
                                            <td>
                                                <span
                                                    className={`badge ${line.direction === "INCOME" ? "b-ok" : "b-bad"}`}
                                                >
                                                    {line.direction === "INCOME" ? "Receita" : "Despesa"}
                                                </span>
                                            </td>
                                            <td>{formatIsoDate(line.billingDate)}</td>
                                            <td className="n">
                                                {formatExactCurrency(line.amount)}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {preview.isPending && preview.fetchStatus !== "idle" ? (
                            <div className="empty">Carregando…</div>
                        ) : items.length === 0 ? (
                            <div className="empty">
                                Nenhum contrato ativo e vigente nesta competência.
                            </div>
                        ) : null}

                        <Pager
                            page={page}
                            totalPages={data.contracts.totalPages}
                            disabled={preview.isFetching}
                            onChange={setPage}
                        />
                    </Panel>
                </div>
            ) : null}
        </>
    )
}
