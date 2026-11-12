import { useRef } from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { reconcileStatementItem } from "../api/statements-api"
import { statementItemsQueryKey } from "./use-statement-items"
import {
    nextCommandAttempt,
    type CommandAttempt,
} from "../../financial-entries/lib/command-key"
import { refreshCompanyFinancialData } from "../../financial-entries/lib/financial-entry-refresh"
import type {
    StatementCandidate,
    StatementItem,
} from "../types/statement.types"

export function useReconcileStatement(
    companyId: string | undefined,
    item: StatementItem,
) {
    const queryClient = useQueryClient()
    const lastAttempt = useRef<CommandAttempt | null>(null)

    return useMutation({
        mutationFn: (candidate: StatementCandidate) => {
            const body = JSON.stringify({
                statementId: item.id,
                expectedStatementVersion: item.version,
                entryId: candidate.entryId,
                expectedEntryVersion: candidate.version,
            })
            const attempt = nextCommandAttempt(lastAttempt.current, body)
            lastAttempt.current = attempt

            return reconcileStatementItem(
                companyId!,
                item.id,
                item.version,
                candidate.entryId,
                candidate.version,
                attempt.key,
            )
        },
        onSuccess: () => {
            lastAttempt.current = null
        },
        onSettled: () =>
            Promise.all([
                queryClient.invalidateQueries({
                    queryKey: statementItemsQueryKey(companyId, item.bankAccountId),
                }),
                companyId
                    ? refreshCompanyFinancialData(queryClient, companyId)
                    : Promise.resolve([]),
            ]),
    })
}
