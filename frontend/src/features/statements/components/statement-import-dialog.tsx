import { useState } from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { useToast } from "../../../components/finemp/toast-context"
import {
    apiErrorCode,
    apiErrorStatus,
} from "../../../lib/api/api-errors"
import { newIdempotencyKey } from "../../../lib/api/idempotency"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { confirmStatementImport } from "../api/statements-api"
import { statementItemsQueryKey } from "../hooks/use-statement-items"
import { StatementPreviewDialog } from "./statement-preview-dialog"
import type { StatementPreviewSession } from "../types/statement.types"

interface StatementImportDialogProps {
    companyId: string
    session: StatementPreviewSession
    accountName: string
    /** Só com FINANCIAL_ENTRY_MANAGE; sem ela a janela é só conferência. */
    canImport: boolean
    onClose: () => void
}

function describeImportError(error: unknown) {
    if (apiErrorCode(error) === "FINANCIAL_PERIOD_CLOSED") {
        return "Há movimentos em período fechado. Nada foi importado."
    }

    switch (apiErrorStatus(error)) {
        case 400:
            return "O arquivo ou a seleção não confere com a prévia. Analise o arquivo de novo. Nada foi importado."
        case 403:
            return "Você não tem permissão para importar extratos nesta empresa."
        case 404:
            return "A importação de extratos não está disponível neste ambiente."
        case 409:
            return "Conflito ao importar: período fechado, movimento já existente na conta ou operação concorrente. Nada foi importado."
        case 413:
            return "O arquivo excede o limite de tamanho da importação."
        case 415:
            return "Tipo de arquivo ou codificação não suportados."
        case 429:
            return "Outra operação de arquivo está em andamento. Tente de novo em instantes."
        default:
            return "Não foi possível confirmar a importação. Tente de novo."
    }
}

/**
 * Confirmação da importação (D2): o usuário escolhe as linhas prontas e
 * confirma conta, moeda em reais e leitura dos alertas. O servidor
 * reprocessa o arquivo; não há baixa nem conciliação (D3).
 */
export function StatementImportDialog({
    companyId,
    session,
    accountName,
    canImport,
    onClose,
}: StatementImportDialogProps) {
    const queryClient = useQueryClient()
    const showToast = useToast()

    const readyRows = session.preview.rows
        .filter((row) => !row.error && row.values)
        .map((row) => row.number)

    const [selected, setSelected] =
        useState<ReadonlySet<number>>(() => new Set(readyRows))

    const [accountConfirmed, setAccountConfirmed] = useState(false)
    const [currencyConfirmed, setCurrencyConfirmed] = useState(false)
    const [warningsConfirmed, setWarningsConfirmed] = useState(false)

    // A mesma chave só pode repetir o mesmo corpo: muda com a seleção.
    const [attempt, setAttempt] = useState<{
        rowsKey: string
        idempotencyKey: string
    } | null>(null)

    const importMutation = useMutation({
        mutationFn: (request: { selectedRows: number[], idempotencyKey: string }) =>
            confirmStatementImport(companyId, {
                session,
                ...request,
            }),
        onSuccess: (receipt) => {
            showToast(`${receipt.itemIds.length} movimento(s) importado(s).`)
            void queryClient.invalidateQueries({
                queryKey: statementItemsQueryKey(companyId, session.bankId),
            })
            onClose()
        },
        onError: (error) => {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            showToast(describeImportError(error), true)
        },
    })

    const selectedRows = readyRows.filter((row) => selected.has(row))

    const confirmed =
        accountConfirmed && currencyConfirmed && warningsConfirmed

    function confirmImport() {
        const rowsKey = selectedRows.join(",")

        const idempotencyKey =
            attempt?.rowsKey === rowsKey
                ? attempt.idempotencyKey
                : newIdempotencyKey()

        setAttempt({ rowsKey, idempotencyKey })
        importMutation.mutate({ selectedRows, idempotencyKey })
    }

    function toggleRow(row: number) {
        setSelected((current) => {
            const next = new Set(current)

            if (next.has(row)) {
                next.delete(row)
            } else {
                next.add(row)
            }

            return next
        })
    }

    const busy = importMutation.isPending

    if (!canImport) {
        return (
            <StatementPreviewDialog
                session={session}
                accountName={accountName}
                onClose={onClose}
            />
        )
    }

    return (
        <StatementPreviewDialog
            session={session}
            accountName={accountName}
            onClose={onClose}
            busy={busy}
            selection={{
                selected,
                onToggle: toggleRow,
            }}
            footer={
                <div
                    style={{
                        display: "grid",
                        gap: 6,
                        marginTop: 12,
                    }}
                >
                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={accountConfirmed}
                            disabled={busy}
                            onChange={(event) => setAccountConfirmed(event.target.checked)}
                        />
                        <span>O arquivo é da conta <b>{accountName}</b>.</span>
                    </label>

                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={currencyConfirmed}
                            disabled={busy}
                            onChange={(event) => setCurrencyConfirmed(event.target.checked)}
                        />
                        <span>Os valores estão em reais (R$).</span>
                    </label>

                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={warningsConfirmed}
                            disabled={busy}
                            onChange={(event) => setWarningsConfirmed(event.target.checked)}
                        />
                        <span>Conferi as linhas e os alertas desta prévia.</span>
                    </label>

                    <p
                        className="help"
                        style={{ margin: 0 }}
                    >
                        A importação grava só os movimentos marcados. Não baixa
                        nem concilia lançamentos.
                    </p>
                </div>
            }
            action={
                <button
                    type="button"
                    className="btn"
                    disabled={busy || !confirmed || selectedRows.length === 0}
                    onClick={confirmImport}
                >
                    {busy
                        ? "Importando…"
                        : `Importar ${selectedRows.length} linha(s)`}
                </button>
            }
        />
    )
}
