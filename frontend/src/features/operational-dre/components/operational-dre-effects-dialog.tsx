import { useState } from "react"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactDecimalCell,
    formatIsoDate,
    isNegativeExactDecimal,
} from "../../../lib/formatters/decimal"
import { formatCompetence } from "../../reports/lib/calendar-labels"
import { useOperationalDreEffects } from "../hooks/use-operational-dre"
import type { OperationalDreLine } from "../lib/dre-structure"

const PAGE_SIZE = 50

const errorMessages = {
    400: "Confira o mês e a linha.",
    403: "Você não tem acesso à DRE operacional desta empresa.",
    404: "O detalhamento da DRE operacional não está disponível neste ambiente.",
}

interface OperationalDreEffectsDialogProps {
    companyId: string | undefined
    month: string
    line: OperationalDreLine
    title: string
    onClose: () => void
}

/** Lançamentos que compõem uma célula da DRE operacional (efeitos do backend). */
export function OperationalDreEffectsDialog({
    companyId,
    month,
    line,
    title,
    onClose,
}: OperationalDreEffectsDialogProps) {
    const [page, setPage] = useState(0)

    const effects = useOperationalDreEffects(companyId, {
        month,
        line,
        page,
        size: PAGE_SIZE,
    })

    return (
        <Modal
            width={860}
            labelledBy="operational-dre-effects-title"
            onClose={onClose}
        >
            <h2 id="operational-dre-effects-title">{title}</h2>

            <p className="help">
                Cada lançamento entra pela competência. Juros, multas e
                descontos na baixa entram no mês do pagamento como diferença
                na baixa. Um lançamento pode aparecer duas vezes, uma por origem.
            </p>

            {effects.isError ? (
                <QueryError
                    error={effects.error}
                    messages={errorMessages}
                    fallback="Não foi possível carregar os lançamentos."
                    onRetry={() => effects.refetch()}
                />
            ) : (
                <div
                    className="tblwrap"
                    style={effects.isPlaceholderData ? { opacity: 0.6 } : undefined}
                >
                    <table>
                        <thead>
                            <tr>
                                <th>Descrição</th>
                                <th>Comp.</th>
                                <th>Pagamento</th>
                                <th>Origem</th>
                                <th className="n">Valor</th>
                            </tr>
                        </thead>

                        <tbody>
                            {effects.data?.items.map((item) => (
                                <tr key={`${item.entryId}|${item.source}`}>
                                    <td style={{ fontSize: 13 }}>{item.description}</td>
                                    <td>{formatCompetence(item.competence)}</td>
                                    <td>{item.paymentDate ? formatIsoDate(item.paymentDate) : "—"}</td>
                                    <td style={{ fontSize: 13 }}>
                                        {item.source === "COMPETENCE" ? "Competência" : "Diferença na baixa"}
                                    </td>
                                    <td className={isNegativeExactDecimal(item.amount) ? "n neg" : "n"}>
                                        {formatExactDecimalCell(item.amount)}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    {effects.data?.items.length === 0 ? (
                        <div className="empty">Nenhum lançamento nesta linha e mês.</div>
                    ) : null}

                    {effects.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : null}
                </div>
            )}

            <Pager
                page={page}
                totalPages={effects.data?.totalPages ?? 0}
                disabled={effects.isFetching}
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
