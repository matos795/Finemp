import { useRef } from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import {
    createFinancialEntry,
    updateFinancialEntry,
} from "../api/financial-entries-api"
import {
    nextCommandAttempt,
    type CommandAttempt,
} from "../lib/command-key"
import type {
    FinancialEntry,
    FinancialEntryCommandValues,
} from "../types/financial-entry.types"

export interface SaveFinancialEntryInput {
    values: FinancialEntryCommandValues
    /** Lançamento lido, na edição; ausente na criação. */
    original?: FinancialEntry
}

/**
 * POST (criação) ou PUT (edição) de um lançamento. A Idempotency-Key é
 * reaproveitada só quando o mesmo corpo é reenviado (ex.: resposta
 * perdida). Depois de qualquer escrita, lançamentos e relatórios da
 * empresa são recarregados: o servidor é a fonte da verdade.
 */
export function useSaveFinancialEntry(
    companyId: string | undefined,
) {
    const queryClient = useQueryClient()
    const lastAttempt = useRef<CommandAttempt | null>(null)

    return useMutation({
        mutationFn: ({ values, original }: SaveFinancialEntryInput) => {
            const body = JSON.stringify({
                entryId: original?.id ?? null,
                expectedVersion: original?.version ?? null,
                values,
            })

            const attempt = nextCommandAttempt(lastAttempt.current, body)
            lastAttempt.current = attempt

            return original
                ? updateFinancialEntry(companyId!, original.id, values, original.version, attempt.key)
                : createFinancialEntry(companyId!, values, attempt.key)
        },
        onSuccess: () => {
            lastAttempt.current = null
        },
        onSettled: () =>
            Promise.all([
                queryClient.invalidateQueries({
                    queryKey: ["companies", companyId, "financial-entries"],
                }),
                queryClient.invalidateQueries({
                    queryKey: ["companies", companyId, "financial-reports"],
                }),
            ]),
    })
}
