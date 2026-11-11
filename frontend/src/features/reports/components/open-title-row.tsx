import {
    formatExactDecimalCell,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { formatCompetence } from "../lib/calendar-labels"
import type { OpenTitle } from "../types/aging.types"

const PENDING_NAME = "Nome ainda não disponível na lista de títulos da API"
const PENDING_CENTER = "Aguardando nome do centro de resultado na API"

interface OpenTitleRowProps {
    title: OpenTitle
    /** Nome do grupo (Agrupar = Sim). Na lista por vencimento a API só envia o ID. */
    partnerName?: string | null
    isIncome: boolean
    /**
     * Abre a baixa do título. Ausente quando o usuário não pode baixar
     * (sem FINANCIAL_ENTRY_MANAGE): a linha fica só para leitura.
     */
    onSettle?: (title: OpenTitle) => void
}

/**
 * Linha da tabela de títulos do protótipo (renderTitulos). Todos os
 * números vêm prontos do backend; aqui só há formatação.
 */
export function OpenTitleRow({
    title,
    partnerName,
    isIncome,
    onSettle,
}: OpenTitleRowProps) {
    const late = title.daysLate > 0

    return (
        <tr>
            <td title={partnerName === undefined ? PENDING_NAME : undefined}>
                {partnerName || "—"}
            </td>
            <td style={{ fontSize: 13 }}>{title.description}</td>
            <td style={{ fontSize: 13 }}>{title.documentNumber}</td>
            <td>{formatCompetence(title.competence)}</td>
            <td>{formatIsoDate(title.dueDate)}</td>
            <td className={late ? "n neg" : "n"}>
                {late ? title.daysLate : "–"}
            </td>
            <td
                style={{ fontSize: 12 }}
                title={title.resultCenterId ? PENDING_CENTER : undefined}
            >
                {title.resultCenterId ? "—" : ""}
            </td>
            <td className="n">{formatExactDecimalCell(title.nominalAmount)}</td>
            <td style={{ whiteSpace: "nowrap" }}>
                {title.paymentDate ? (
                    <span className="badge b-neu">
                        pago em {formatIsoDate(title.paymentDate)}
                    </span>
                ) : onSettle ? (
                    <button
                        type="button"
                        className={`btn ${isIncome ? "ent" : "sai"} sm`}
                        aria-label={`${isIncome ? "Receber" : "Pagar"} ${title.description}`}
                        onClick={() => onSettle(title)}
                    >
                        {isIncome ? "Receber" : "Pagar"}
                    </button>
                ) : null}
            </td>
        </tr>
    )
}
