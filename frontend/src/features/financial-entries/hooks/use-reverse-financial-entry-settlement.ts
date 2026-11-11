import { useRef } from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { reverseFinancialEntrySettlement } from "../api/financial-entries-api"
import {
    nextCommandAttempt,
    type CommandAttempt,
} from "../lib/command-key"
import { refreshCompanyFinancialData } from "../lib/financial-entry-refresh"
import type { FinancialEntry } from "../types/financial-entry.types"

/**
 * Estorno (POST …/settlement-reversals) de um lançamento lido. A
 * Idempotency-Key só é reaproveitada quando o mesmo corpo é reenviado;
 * depois, lançamentos e relatórios da mesma Company são recarregados.
 */
export function useReverseFinancialEntrySettlement(
    companyId: string,
) {
    const queryClient = useQueryClient()
    const lastAttempt = useRef<CommandAttempt | null>(null)

    return useMutation({
        mutationFn: (entry: FinancialEntry) => {
            const body = JSON.stringify({
                action: "reverse",
                entryId: entry.id,
                expectedVersion: entry.version,
            })

            const attempt = nextCommandAttempt(lastAttempt.current, body)
            lastAttempt.current = attempt

            return reverseFinancialEntrySettlement(companyId, entry.id, entry.version, attempt.key)
        },
        retry: false,
        onSuccess: () => {
            lastAttempt.current = null
        },
        onSettled: () => refreshCompanyFinancialData(queryClient, companyId),
    })
}
