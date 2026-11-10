import { useRef } from "react"
import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { settleFinancialEntry } from "../api/financial-entries-api"
import {
    nextCommandAttempt,
    type CommandAttempt,
} from "../lib/command-key"
import type {
    FinancialEntry,
    FinancialEntrySettlementValues,
} from "../types/financial-entry.types"

/**
 * Baixa (POST …/settlements) de um lançamento lido. A Idempotency-Key só
 * é reaproveitada quando o mesmo corpo é reenviado (ex.: resposta perdida
 * ou duplo clique), e o backend devolve o mesmo recibo sem baixa dupla.
 * Depois, lançamentos e relatórios (fluxo, aging, DRE) da mesma Company
 * são recarregados.
 */
export function useSettleFinancialEntry(
    companyId: string,
) {
    const queryClient = useQueryClient()
    const lastAttempt = useRef<CommandAttempt | null>(null)

    return useMutation({
        mutationFn: ({ entry, values }: { entry: FinancialEntry, values: FinancialEntrySettlementValues }) => {
            const body = JSON.stringify({
                entryId: entry.id,
                expectedVersion: entry.version,
                values,
            })

            const attempt = nextCommandAttempt(lastAttempt.current, body)
            lastAttempt.current = attempt

            return settleFinancialEntry(companyId, entry.id, values, entry.version, attempt.key)
        },
        retry: false,
        onSuccess: () => {
            lastAttempt.current = null
        },
        onSettled: () =>
            Promise.all([
                queryClient.invalidateQueries({ queryKey: ["companies", companyId, "financial-entries"] }),
                queryClient.invalidateQueries({ queryKey: ["companies", companyId, "financial-reports"] }),
            ]),
    })
}
