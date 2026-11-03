import { useRef } from "react"
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
import { ignoreStatementItem } from "../api/statements-api"
import { statementItemsQueryKey } from "../hooks/use-statement-items"
import type { StatementItem } from "../types/statement.types"

interface IgnoreStatementButtonProps {
    companyId: string | undefined
    item: StatementItem
    /** FINANCIAL_ENTRY_MANAGE: sem ela o botão fica inativo (só UX). */
    canIgnore: boolean
}

function describeIgnoreError(error: unknown) {
    if (apiErrorCode(error) === "FINANCIAL_PERIOD_CLOSED") {
        return "O mês deste movimento está fechado. Nada foi alterado."
    }

    switch (apiErrorStatus(error)) {
        case 403:
            return "Você não tem permissão para alterar extratos nesta empresa."
        case 404:
            return "Ignorar movimentos não está disponível neste ambiente."
        case 409:
            return "O movimento foi alterado por outra pessoa. A lista foi atualizada."
        case 429:
            return "Muitas operações ao mesmo tempo. Tente de novo em instantes."
        default:
            return "Não foi possível ignorar o movimento."
    }
}

/** "Ignorar" do protótipo (ignorarEx): sem confirmação, como lá. */
export function IgnoreStatementButton({
    companyId,
    item,
    canIgnore,
}: IgnoreStatementButtonProps) {
    const queryClient = useQueryClient()
    const showToast = useToast()

    // Mesma chave para repetir o mesmo pedido (mesma versão) após falha.
    const keyForVersion = useRef<{ version: number, key: string } | null>(null)

    const ignore = useMutation({
        mutationFn: () => {
            if (keyForVersion.current?.version !== item.version) {
                keyForVersion.current = {
                    version: item.version,
                    key: newIdempotencyKey(),
                }
            }

            return ignoreStatementItem(
                companyId!,
                item.id,
                item.version,
                keyForVersion.current.key,
            )
        },
        onError: (error) => {
            if (apiErrorStatus(error) === 401) {
                queryClient.setQueryData(currentUserQueryKey, null)
                return
            }

            showToast(describeIgnoreError(error), true)
        },
        onSettled: () =>
            queryClient.invalidateQueries({
                queryKey: statementItemsQueryKey(companyId, item.bankAccountId),
            }),
    })

    return (
        <button
            type="button"
            className="btn sec sm"
            disabled={!canIgnore || !companyId || ignore.isPending}
            title={canIgnore ? undefined : "Sem permissão para alterar extratos"}
            onClick={() => ignore.mutate()}
        >
            Ignorar
        </button>
    )
}
