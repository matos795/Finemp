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
import { refreshCompanyFinancialData } from "../lib/financial-entry-refresh"
import {
    settlementCommand,
    type SettlementTarget,
} from "../lib/financial-entry-settlement"
import type { FinancialEntrySettlementValues } from "../types/financial-entry.types"

/**
 * Baixa (POST …/settlements) de um título, aberto em Lançamentos ou nos
 * relatórios de títulos em aberto (#330). A Idempotency-Key só
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
        mutationFn: ({ target, values }: { target: SettlementTarget, values: FinancialEntrySettlementValues }) => {
            const command = settlementCommand(target, values)
            const attempt = nextCommandAttempt(lastAttempt.current, JSON.stringify(command))
            lastAttempt.current = attempt

            return settleFinancialEntry(companyId, command.entryId, command.values, command.expectedVersion, attempt.key)
        },
        retry: false,
        onSuccess: () => {
            lastAttempt.current = null
        },
        onSettled: () => refreshCompanyFinancialData(queryClient, companyId),
    })
}
