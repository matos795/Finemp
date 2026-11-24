import { useEffect, useRef } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getAccountingPortfolio, getCompanySyncStatus, getOrganizationAccess } from "../api/accounting-area-api"
import { accountingQueryKey } from "../lib/query-keys"
import { isRunActive, succeededSignature } from "../lib/sync-labels"

/** Id do usuário da sessão: separa o cache de cada usuário (ver lib/query-keys). */
export function useSessionUserId() {
    return useCompanyAccess().user.id
}

export function useOrganizationAccess() {
    const userId = useSessionUserId()

    return useQuery({
        queryKey: accountingQueryKey(userId, "organization-access"),
        queryFn: getOrganizationAccess,
        staleTime: 30_000,
        retry: false,
    })
}

/** Sem cache longo: revogação de grant precisa refletir na mesma sessão. */
export function useAccountingPortfolio(enabled = true) {
    const userId = useSessionUserId()

    return useQuery({
        queryKey: accountingQueryKey(userId, "portfolio"),
        queryFn: getAccountingPortfolio,
        enabled,
        staleTime: 0,
        retry: false,
    })
}

export function useCompanySyncStatus(companyId: string | null) {
    const userId = useSessionUserId()
    const queryClient = useQueryClient()
    const query = useQuery({
        queryKey: accountingQueryKey(userId, "sync-status", companyId),
        queryFn: () => getCompanySyncStatus(companyId as string),
        enabled: companyId !== null,
        retry: false,
        // Enquanto houver execução solicitada ou em andamento, acompanha o estado.
        refetchInterval: (current) =>
            current.state.data?.items.some((source) => isRunActive(source.latestRun)) ? 5_000 : false,
    })

    // Nova sincronização concluída: contas, movimentos e DRE da empresa mudaram no backend.
    const signature = succeededSignature(query.data)
    const previous = useRef<{ companyId: string | null; signature: string | null }>({ companyId, signature: null })

    useEffect(() => {
        const before = previous.current
        previous.current = { companyId, signature }

        if (
            companyId !== null &&
            before.companyId === companyId &&
            before.signature !== null &&
            signature !== null &&
            before.signature !== signature
        ) {
            for (const kind of ["accounts", "movements", "dre"]) {
                void queryClient.invalidateQueries({ queryKey: accountingQueryKey(userId, kind, companyId) })
            }
        }
    }, [companyId, signature, queryClient, userId])

    return query
}
