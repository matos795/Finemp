import { useQuery } from "@tanstack/react-query"
import { getAccountBalances } from "../api/reports-api"

export function useAccountBalances(companyId: string | undefined) {
    return useQuery({
        queryKey: [
            "companies",
            companyId,
            "financial-reports",
            "account-balances",
        ],
        queryFn: () => getAccountBalances(companyId!),
        enabled: Boolean(companyId),
    })
}
