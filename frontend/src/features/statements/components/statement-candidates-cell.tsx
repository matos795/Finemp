import type { ReactNode } from "react"
import { formatIsoDate } from "../../../lib/formatters/decimal"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useStatementCandidates } from "../hooks/use-statement-candidates"
import type { StatementCandidate } from "../types/statement.types"

interface StatementCandidatesCellsProps {
    companyId: string | undefined
    statementId: string
    /** Ações do movimento pendente (Criar lançamento, Ignorar). */
    actions: ReactNode
}

/** Rótulo do protótipo: "Já pago · 20/10/2026 · descrição" (40 caracteres). */
function candidateLabel(candidate: StatementCandidate) {
    const paid = Boolean(candidate.paymentDate)
    const date = candidate.paymentDate ?? candidate.dueDate

    return `${paid ? "Já pago" : "Em aberto"} · ${formatIsoDate(date)} · ${candidate.description.slice(0, 40)}`
}

function CandidatesContent({
    candidates,
}: {
    candidates: ReturnType<typeof useStatementCandidates>
}) {
    if (candidates.isPending) {
        return <span className="mut">Buscando sugestões…</span>
    }

    if (candidates.isError) {
        return (
            <span className="mut">
                {apiErrorStatus(candidates.error) === 404
                    ? "Sugestões indisponíveis neste ambiente"
                    : "Não foi possível buscar sugestões"}
            </span>
        )
    }

    const items = candidates.data.items

    if (!items.length) {
        return <span className="mut">Sem lançamento correspondente</span>
    }

    return (
        <select
            aria-label="Lançamento sugerido"
            style={{ maxWidth: 340 }}
            defaultValue={items[0].entryId}
        >
            {items.map((candidate) => (
                <option
                    key={candidate.entryId}
                    value={candidate.entryId}
                >
                    {candidateLabel(candidate)}
                </option>
            ))}

            <option value="">— nenhum destes —</option>
        </select>
    )
}

/**
 * Células "Lançamento no sistema" e "Ações" de um movimento pendente:
 * as sugestões do servidor num select, como no protótipo. Só consulta
 * (D3); "Conciliar" aparece com sugestões, mas fica inativo (PA1).
 */
export function StatementCandidatesCells({
    companyId,
    statementId,
    actions,
}: StatementCandidatesCellsProps) {
    const candidates =
        useStatementCandidates(companyId, statementId, true)

    const hasCandidates = Boolean(candidates.data?.items.length)

    return (
        <>
            <td>
                <CandidatesContent candidates={candidates} />
            </td>

            <td style={{ whiteSpace: "nowrap" }}>
                {hasCandidates && (
                    <>
                        <button
                            type="button"
                            className="btn ok sm"
                            disabled
                            title="Conciliar ainda não existe na API"
                        >
                            Conciliar
                        </button>{" "}
                    </>
                )}

                {actions}
            </td>
        </>
    )
}
