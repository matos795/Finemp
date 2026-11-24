import { useQuery } from "@tanstack/react-query"
import { useCompanyAccess } from "../../companies/company-access-context"
import { getAccountingPortfolio, getCompanySyncStatus, getOrganizationAccess } from "../api/accounting-area-api"
import { accountingQueryKey } from "../lib/query-keys"

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

    return useQuery({
        queryKey: accountingQueryKey(userId, "sync-status", companyId),
        queryFn: () => getCompanySyncStatus(companyId as string),
        enabled: companyId !== null,
        retry: false,
    })
}
