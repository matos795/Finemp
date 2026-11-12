import {
    useState,
    type ReactNode,
} from "react"
import { useQueryClient } from "@tanstack/react-query"
import { formatIsoDate } from "../../../lib/formatters/decimal"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useToast } from "../../../components/finemp/toast-context"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useStatementCandidates } from "../hooks/use-statement-candidates"
import { useReconcileStatement } from "../hooks/use-statement-reconciliation"
import { describeStatementResolutionError } from "../lib/statement-resolution-errors"
import type {
    StatementCandidate,
    StatementItem,
} from "../types/statement.types"

interface StatementCandidatesCellsProps {
    companyId: string | undefined
    item: StatementItem
    canManage: boolean
    onResolved: () => void
    actions: ReactNode
}

/** Rótulo do protótipo: "Já pago · 20/10/2026 · descrição" (40 caracteres). */
function candidateLabel(candidate: StatementCandidate) {
    const paid = Boolean(candidate.paymentDate)
    const date = candidate.paymentDate ?? candidate.dueDate

    return `${paid ? "Já pago" : "Em aberto"} · ${formatIsoDate(date)} · ${candidate.description.slice(0, 40)}`
}

function loadedCandidateItems(
    candidates: ReturnType<typeof useStatementCandidates>,
) {
    const seen = new Set<string>()

    return (candidates.data?.pages ?? [])
        .flatMap((page) => page.items)
        .filter((candidate) => {
            if (seen.has(candidate.entryId)) {
                return false
            }

            seen.add(candidate.entryId)
            return true
        })
}

function CandidatesContent({
    candidates,
    selectedEntryId,
    onSelect,
}: {
    candidates: ReturnType<typeof useStatementCandidates>
    selectedEntryId: string
    onSelect: (entryId: string) => void
}) {
    if (candidates.isPending) {
        return <span className="mut">Buscando sugestões…</span>
    }

    if (candidates.isError && !candidates.data) {
        return (
            <span className="mut">
                {apiErrorStatus(candidates.error) === 404
                    ? "Sugestões indisponíveis neste ambiente"
                    : "Não foi possível buscar sugestões"}
            </span>
        )
    }

    const items = loadedCandidateItems(candidates)

    if (!items.length) {
        return <span className="mut">Sem lançamento correspondente</span>
    }

    const value = items.some((candidate) => candidate.entryId === selectedEntryId)
        ? selectedEntryId
        : items[0].entryId

    return (
        <div
            style={{
                display: "grid",
                gap: 4,
                maxWidth: 340,
            }}
        >
            <select
                aria-label="Lançamento sugerido"
                value={value}
                onChange={(event) => onSelect(event.target.value)}
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

            {candidates.hasNextPage && (
                <button
                    type="button"
                    className="btn sec sm"
                    disabled={candidates.isFetchingNextPage}
                    onClick={() => void candidates.fetchNextPage()}
                >
                    {candidates.isFetchingNextPage
                        ? "Carregando candidatos…"
                        : "Carregar mais candidatos"}
                </button>
            )}

            {candidates.isFetchNextPageError && (
                <span className="mut">
                    Não foi possível carregar mais candidatos. Tente novamente.
                </span>
            )}
        </div>
    )
}

export function StatementCandidatesCells({
    companyId,
    item,
    canManage,
    onResolved,
    actions,
}: StatementCandidatesCellsProps) {
    const candidates =
        useStatementCandidates(companyId, item.id, true)
    const [selectedEntryId, setSelectedEntryId] = useState("")
    const reconcile = useReconcileStatement(companyId, item)
    const queryClient = useQueryClient()
    const showToast = useToast()

    const items = loadedCandidateItems(candidates)
    const selected =
        items.find((candidate) => candidate.entryId === selectedEntryId) ??
        items[0]

    function submit() {
        if (!selected) {
            return
        }

        reconcile.mutate(selected, {
            onSuccess: () => {
                showToast("Movimento conciliado.")
                onResolved()
            },
            onError: (error) => {
                if (apiErrorStatus(error) === 401) {
                    queryClient.setQueryData(currentUserQueryKey, null)
                    return
                }

                showToast(describeStatementResolutionError(error), true)
            },
        })
    }

    return (
        <>
            <td>
                <CandidatesContent
                    candidates={candidates}
                    selectedEntryId={selectedEntryId}
                    onSelect={setSelectedEntryId}
                />
            </td>

            <td style={{ whiteSpace: "nowrap" }}>
                {selected && (
                    <>
                        <button
                            type="button"
                            className="btn ok sm"
                            disabled={!canManage || reconcile.isPending}
                            title={canManage ? undefined : "Sem permissão para conciliar extratos"}
                            onClick={submit}
                        >
                            {reconcile.isPending ? "Conciliando…" : "Conciliar"}
                        </button>{" "}
                    </>
                )}

                {actions}
            </td>
        </>
    )
}
