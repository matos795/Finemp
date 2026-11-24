import { useQuery } from "@tanstack/react-query"
import { getAccountingPortfolio, getCompanySyncStatus, getOrganizationAccess } from "../api/accounting-area-api"

export const organizationAccessQueryKey = ["organization-access"] as const
export const accountingPortfolioQueryKey = ["accounting-portfolio"] as const

export function useOrganizationAccess() {
    return useQuery({
        queryKey: organizationAccessQueryKey,
        queryFn: getOrganizationAccess,
        staleTime: 30_000,
        retry: false,
    })
}

/** Sem cache longo: revogação de grant precisa refletir na mesma sessão. */
export function useAccountingPortfolio(enabled = true) {
    return useQuery({
        queryKey: accountingPortfolioQueryKey,
        queryFn: getAccountingPortfolio,
        enabled,
        staleTime: 0,
        retry: false,
    })
}

export function useCompanySyncStatus(companyId: string | null) {
    return useQuery({
        queryKey: ["accounting-area", "sync-status", companyId],
        queryFn: () => getCompanySyncStatus(companyId as string),
        enabled: companyId !== null,
        retry: false,
    })
}
