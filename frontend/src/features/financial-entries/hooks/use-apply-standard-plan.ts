import {
    useMutation,
    useQueryClient,
} from "@tanstack/react-query"
import { applyStandardFinancialCategoryPlan } from "../api/financial-catalogs-api"
import {
    describeStandardPlanFailure,
    refreshFinancialCategories,
} from "../lib/financial-onboarding"

/**
 * Aplica o plano padrão FINEMP por ação explícita do usuário. Sem retry
 * automático; depois de sucesso ou de conflito, o catálogo da mesma
 * Company é relido para liberar "+ Entrada" / "+ Saída".
 */
export function useApplyStandardPlan(
    companyId: string,
) {
    const queryClient = useQueryClient()

    return useMutation({
        mutationFn: () => applyStandardFinancialCategoryPlan(companyId),
        retry: false,
        onSuccess: () => refreshFinancialCategories(queryClient, companyId),
        onError: (error) => {
            if (describeStandardPlanFailure(error).refreshCatalog) {
                return refreshFinancialCategories(queryClient, companyId)
            }
        },
    })
}
