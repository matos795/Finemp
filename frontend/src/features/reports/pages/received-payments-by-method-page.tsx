import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency, formatExactDecimalCell } from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getReceivedPaymentsByMethod } from "../api/reports-api"

function monthStart(today: string) {
    return `${today.slice(0, 7)}-01`
}

/**
 * Clientes › Recebimentos por forma.
 *
 * O backend define quais lançamentos contam como recebidos e soma os valores.
 * O frontend apenas escolhe o período e apresenta os agregados exatos.
 */
export function ReceivedPaymentsByMethodPage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id

    const today = todayIso()
    const [from, setFrom] = useState(() => monthStart(today))
    const [to, setTo] = useState(today)

    const validPeriod = Boolean(from && to && from <= to)

    const report = useQuery({
        queryKey: ["received-payments-by-method", companyId, from, to] as const,
        queryFn: () => getReceivedPaymentsByMethod(companyId!, from, to),
        enabled: Boolean(companyId) && validPeriod,
        retry: false,
    })

    const data = report.data

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="receivedFrom">De</label>
                    <input
                        id="receivedFrom"
                        type="date"
                        value={from}
                        onChange={(event) => setFrom(event.target.value)}
                    />
                </div>

                <div className="f">
                    <label htmlFor="receivedTo">Até</label>
                    <input
                        id="receivedTo"
                        type="date"
                        value={to}
                        onChange={(event) => setTo(event.target.value)}
                    />
                </div>
            </div>

            {!validPeriod ? (
                <div className="alert e">
                    A data inicial deve ser anterior ou igual à data final.
                </div>
            ) : null}

            {report.isError ? (
                <QueryError
                    error={report.error}
                    messages={{
                        400: "Confira o período informado.",
                        403: "Você não tem acesso aos recebimentos desta empresa.",
                    }}
                    fallback="Não foi possível carregar os recebimentos por forma."
                    onRetry={() => report.refetch()}
                />
            ) : (
                <>
                    <div className="cards">
                        <div className="card">
                            <div className="lb">Total recebido</div>
                            <div className="vl">
                                {data ? formatExactCurrency(data.totalAmount) : "—"}
                            </div>
                            <div className="sub">
                                {data
                                    ? `${data.totalCount} recebimento(s) no período`
                                    : "Carregando…"}
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">Formas utilizadas</div>
                            <div className="vl">
                                {data ? data.methods.length : "—"}
                            </div>
                            <div className="sub">
                                Conforme informado nos lançamentos recebidos
                            </div>
                        </div>
                    </div>

                    <div
                        className="panel"
                        style={{ padding: 0 }}
                    >
                        <div
                            className="tblwrap"
                            style={report.isPlaceholderData ? { opacity: 0.6 } : undefined}
                        >
                            <table>
                                <thead>
                                    <tr>
                                        <th>Forma de recebimento</th>
                                        <th className="n">Recebimentos</th>
                                        <th className="n">Valor recebido</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {data?.methods.map((method) => (
                                        <tr key={method.paymentMethod}>
                                            <td>{method.paymentMethod}</td>
                                            <td className="n">{method.paymentCount}</td>
                                            <td className="n">
                                                {formatExactDecimalCell(method.totalAmount)}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>

                                {data ? (
                                    <tfoot>
                                        <tr>
                                            <td>Total</td>
                                            <td className="n">{data.totalCount}</td>
                                            <td className="n">
                                                {formatExactDecimalCell(data.totalAmount)}
                                            </td>
                                        </tr>
                                    </tfoot>
                                ) : null}
                            </table>
                        </div>

                        {report.isPending && report.fetchStatus !== "idle" ? (
                            <div className="empty">Carregando…</div>
                        ) : data?.methods.length === 0 ? (
                            <div className="empty">
                                Nenhum recebimento encontrado neste período.
                            </div>
                        ) : null}
                    </div>
                </>
            )}
        </>
    )
}
