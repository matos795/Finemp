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

const definitiveFailureStatuses = new Set([
    400,
    401,
    403,
    404,
    409,
    413,
    415,
    429,
])

function isDefinitiveImportFailure(error: unknown) {
    const status = apiErrorStatus(error)

    return status !== undefined && definitiveFailureStatuses.has(status)
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

    const declaredCurrency =
        session.preview.currency?.trim().toUpperCase()

    const unsupportedCurrency =
        Boolean(
            declaredCurrency &&
            declaredCurrency !== "BRL",
        )

    // A mesma chave só pode repetir o mesmo corpo: muda com a seleção.
    const [attempt, setAttempt] = useState<{
        selectedRows: number[]
        idempotencyKey: string
    } | null>(null)

    const [ambiguousFailure, setAmbiguousFailure] = useState(false)

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
            if (isDefinitiveImportFailure(error)) {
                setAttempt(null)
                setAmbiguousFailure(false)

                if (apiErrorStatus(error) === 401) {
                    queryClient.setQueryData(currentUserQueryKey, null)
                    return
                }

                showToast(describeImportError(error), true)
                return
            }

            setAmbiguousFailure(true)
            showToast(
                "Não foi possível confirmar se a importação terminou. Mantenha esta tentativa e use Tentar novamente para repetir com segurança.",
                true,
            )
        },
    })

    const selectedRows = readyRows.filter((row) => selected.has(row))

    const confirmed =
        !unsupportedCurrency &&
        accountConfirmed &&
        currencyConfirmed &&
        warningsConfirmed

    function confirmImport() {
        if (attempt && ambiguousFailure) {
            setAmbiguousFailure(false)
            importMutation.mutate(attempt)
            return
        }

        const nextAttempt = {
            selectedRows: [...selectedRows],
            idempotencyKey: newIdempotencyKey(),
        }

        setAttempt(nextAttempt)
        importMutation.mutate(nextAttempt)
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

    const requestPending = importMutation.isPending
    const interactionLocked = requestPending || ambiguousFailure

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
            busy={interactionLocked}
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
                            disabled={interactionLocked}
                            onChange={(event) => setAccountConfirmed(event.target.checked)}
                        />
                        <span>O arquivo é da conta <b>{accountName}</b>.</span>
                    </label>

                    {unsupportedCurrency ? (
                        <div className="alert w">
                            Este arquivo informa moeda <b>{declaredCurrency}</b>.
                            A importação de extratos atualmente aceita apenas reais (BRL).
                        </div>
                    ) : (
                        <label className="chk">
                            <input
                                type="checkbox"
                                checked={currencyConfirmed}
                                disabled={interactionLocked}
                                onChange={(event) =>
                                    setCurrencyConfirmed(event.target.checked)
                                }
                            />
                            <span>Os valores estão em reais (R$).</span>
                        </label>
                    )}

                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={warningsConfirmed}
                            disabled={interactionLocked}
                            onChange={(event) => setWarningsConfirmed(event.target.checked)}
                        />
                        <span>Conferi as linhas e os alertas desta prévia.</span>
                    </label>

                    {ambiguousFailure && (
                        <div className="alert w">
                            O resultado da última tentativa é incerto. Para evitar
                            duplicidade, a seleção fica bloqueada até você repetir
                            exatamente a mesma importação.
                        </div>
                    )}

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
                    disabled={
                        requestPending ||
                        (!ambiguousFailure &&
                            (!confirmed || selectedRows.length === 0))
                    }
                    onClick={confirmImport}
                >
                    {requestPending
                        ? "Importando…"
                        : ambiguousFailure && attempt
                            ? `Tentar novamente (${attempt.selectedRows.length} linha(s))`
                            : `Importar ${selectedRows.length} linha(s)`}
                </button>
            }
        />
    )
}
