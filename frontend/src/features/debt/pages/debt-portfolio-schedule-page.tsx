import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import {
    formatExactCurrency,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getDebtPortfolioSchedule } from "../api/debts-api"
import type { DebtOrigin } from "../types/debt.types"

const originLabel: Record<DebtOrigin, string> = {
    BANK: "Banco",
    SUPPLIER: "Fornecedor",
    TAX: "Imposto",
    THIRD_PARTY: "Terceiro",
}

export function DebtPortfolioSchedulePage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <DebtPortfolioScheduleCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function DebtPortfolioScheduleCompanyPage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const [month, setMonth] = useState(todayIso().slice(0, 7))
    const [page, setPage] = useCompanyPage(companyId, month)

    const schedule = useQuery({
        queryKey: ["debt-portfolio-schedule", companyId, month, page] as const,
        queryFn: ({ signal }) =>
            getDebtPortfolioSchedule(companyId!, month, page, signal),
        enabled: Boolean(companyId && /^\d{4}-\d{2}$/.test(month)),
        placeholderData: (previous) => previous,
        retry: false,
    })

    const data = schedule.isError ? undefined : schedule.data
    const items = data?.items ?? []

    useEffect(() => {
        const totalPages = data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [data?.totalPages, page, setPage])

    return (
        <>
            <Panel
                title="Cronograma de pagamentos"
                help="Agenda contratual das parcelas das dívidas cadastradas. Não indica se uma parcela foi paga, está em aberto ou em atraso."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="debtScheduleMonth">Competência</label>
                        <input
                            id="debtScheduleMonth"
                            type="month"
                            value={month}
                            onChange={(event) => {
                                setMonth(event.target.value)
                                setPage(0)
                            }}
                        />
                    </div>
                </div>
            </Panel>

            {schedule.isError ? (
                <QueryError
                    error={schedule.error}
                    messages={{
                        400: "Confira a competência informada.",
                        403: "Você não tem acesso ao endividamento desta empresa.",
                    }}
                    fallback="Não foi possível carregar o cronograma contratual."
                    onRetry={() => schedule.refetch()}
                />
            ) : null}

            {data ? (
                <Panel
                    title="Parcelas da competência"
                    help="Valores calculados pelo backend a partir do sistema de amortização cadastrado em cada acordo."
                >
                    <div
                        className="tblwrap"
                        style={schedule.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Vencimento</th>
                                    <th>Origem</th>
                                    <th>Credor</th>
                                    <th>Acordo</th>
                                    <th className="n">Parcela</th>
                                    <th className="n">Amortização</th>
                                    <th className="n">Juros</th>
                                    <th className="n">Valor</th>
                                    <th className="n">Saldo contratual após</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((item) => (
                                    <tr key={item.id}>
                                        <td>{formatIsoDate(item.dueDate)}</td>
                                        <td>
                                            <span className="badge b-neu">
                                                {originLabel[item.origin]}
                                            </span>
                                        </td>
                                        <td>{item.creditorName ?? "—"}</td>
                                        <td>{item.description}</td>
                                        <td className="n">#{item.installmentNumber}</td>
                                        <td className="n">
                                            {formatExactCurrency(item.amortizationAmount)}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(item.interestAmount)}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(item.installmentAmount)}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(item.balanceAfter)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {schedule.isPending && schedule.fetchStatus !== "idle" ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            Nenhuma parcela contratual nesta competência.
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={data.totalPages}
                        disabled={schedule.isFetching}
                        onChange={setPage}
                    />
                </Panel>
            ) : null}
        </>
    )
}
