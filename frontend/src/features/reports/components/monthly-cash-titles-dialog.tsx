import { useState } from "react"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactDecimalCell,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { useMonthlyCashTitles } from "../hooks/use-cash-totals"
import type { MonthlyCashDetailFilter } from "../types/cash-totals.types"

const PAGE_SIZE = 50

const errorMessages = {
    400: "Confira o mês e a categoria.",
    403: "Você não tem acesso aos títulos desta empresa.",
    404: "O detalhamento por título não está disponível neste ambiente.",
}

interface MonthlyCashTitlesDialogProps {
    companyId: string | undefined
    filter: MonthlyCashDetailFilter
    title: string
    onClose: () => void
}

/**
 * Títulos que compõem uma célula do fluxo mensal (4º nível do protótipo).
 * Data, valor e situação vêm do backend; aqui só há formatação.
 */
export function MonthlyCashTitlesDialog({
    companyId,
    filter,
    title,
    onClose,
}: MonthlyCashTitlesDialogProps) {
    const [page, setPage] = useState(0)

    const titles =
        useMonthlyCashTitles(companyId, filter, page, PAGE_SIZE)

    const isIncome = filter.direction === "INCOME"

    return (
        <Modal
            width={860}
            labelledBy="monthly-cash-titles-title"
            onClose={onClose}
        >
            <h2 id="monthly-cash-titles-title">{title}</h2>

            <p className="help">
                {filter.state === "REALIZED"
                    ? `Títulos ${isIncome ? "recebidos" : "pagos"} no mês, pela data e pelo valor efetivo da baixa.`
                    : "Títulos em aberto com vencimento no mês, pelo valor nominal."}
            </p>

            {titles.isError ? (
                <QueryError
                    error={titles.error}
                    messages={errorMessages}
                    fallback="Não foi possível carregar os títulos."
                    onRetry={() => titles.refetch()}
                />
            ) : (
                <div
                    className="tblwrap"
                    style={titles.isPlaceholderData ? { opacity: 0.6 } : undefined}
                >
                    <table>
                        <thead>
                            <tr>
                                <th>Data</th>
                                <th>Descrição</th>
                                <th>Vencimento</th>
                                <th className="n">Valor</th>
                            </tr>
                        </thead>

                        <tbody>
                            {titles.data?.items.map((item) => (
                                <tr key={item.id}>
                                    <td>{formatIsoDate(item.cashDate)}</td>
                                    <td style={{ fontSize: 13 }}>
                                        {item.state === "OPEN" ? (
                                            <>
                                                <span
                                                    className="badge b-gold"
                                                    style={{ fontSize: 10, padding: "1px 5px" }}
                                                >
                                                    aberto
                                                </span>{" "}
                                            </>
                                        ) : null}
                                        {item.description}
                                    </td>
                                    <td>{formatIsoDate(item.dueDate)}</td>
                                    <td className="n">{formatExactDecimalCell(item.amount)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    {titles.data?.items.length === 0 ? (
                        <div className="empty">Nenhum título neste mês.</div>
                    ) : null}

                    {titles.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : null}
                </div>
            )}

            <Pager
                page={page}
                totalPages={titles.data?.totalPages ?? 0}
                disabled={titles.isFetching}
                onChange={setPage}
            />

            <div className="mact">
                <button
                    type="button"
                    className="btn sec"
                    onClick={onClose}
                >
                    Fechar
                </button>
            </div>
        </Modal>
    )
}
