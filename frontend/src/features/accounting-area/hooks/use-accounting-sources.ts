import { useQuery } from "@tanstack/react-query"
import { searchImportedAccounts } from "../api/accounting-data-api"
import { accountingV2Access, knownSourceIds } from "../lib/accounting-v2"
import { accountingQueryKey } from "../lib/query-keys"
import { useCompanySyncStatus, useSessionUserId } from "./use-accounting-area-queries"

export interface AccountingSourceOption {
    id: string
    label: string
}

/**
 * Fontes contábeis da empresa visíveis à sessão. As leituras v2 exigem
 * `accountingSourceId`; sem contrato novo, a lista vem de /accounting-sources
 * (exige QUESTOR_SYNC) e das contas importadas que a sessão já pode ler.
 */
export function useAccountingSources(companyId: string | null, permissions: readonly string[]) {
    const userId = useSessionUserId()
    const access = accountingV2Access(permissions)
    const sync = useCompanySyncStatus(companyId)
    const accounts = useQuery({
        queryKey: accountingQueryKey(userId, "accounts", companyId, "sources"),
        queryFn: () => searchImportedAccounts(companyId as string, { status: null, search: "", page: 0, size: 100 }),
        enabled: companyId !== null && access.chart,
        retry: false,
    })

    const ids = knownSourceIds(sync.isSuccess ? sync.data : null, accounts.isSuccess ? accounts.data : null)
    const options: AccountingSourceOption[] = ids.map((id, index) => {
        const synced = sync.data?.items.find((item) => item.accountingSourceId === id)

        return { id, label: synced ? `Fonte ${index + 1} · ${synced.type}` : `Fonte ${index + 1}` }
    })

    return {
        options,
        loading: companyId !== null && (sync.isLoading || (access.chart && accounts.isLoading)),
    }
}

/** Fonte efetiva: a escolhida, se ainda visível; senão a primeira. */
export function effectiveSource(options: AccountingSourceOption[], chosen: string | null) {
    return options.find((option) => option.id === chosen)?.id ?? options[0]?.id ?? null
}
