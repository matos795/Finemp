import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query"
import { refreshCompanyFinancialData } from "../../financial-entries/lib/financial-entry-refresh"
import {
    createFinancialCategoryGroup,
    createFinancialSubcategory,
    deleteFinancialCategory,
    listFinancialCategories,
    renameFinancialCategory,
    updateFinancialCategoryClassification,
} from "../api/financial-categories-api"
import type {
    FinancialCategory,
    FinancialCategoryClassification,
    FinancialCategoryDirection,
    FinancialCategoryFilter,
} from "../types/financial-category.types"

/** Página do plano de contas da Company ativa; nunca reaproveita outra Company. */
export function useFinancialCategoryPage(
    companyId: string | undefined,
    filter: FinancialCategoryFilter,
    page: number,
) {
    return useQuery({
        queryKey: ["companies", companyId, "financial-categories", "catalog-page", { filter, page }],
        queryFn: () => listFinancialCategories(companyId!, filter, page),
        enabled: Boolean(companyId),
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId ? previousData : undefined,
    })
}

/**
 * Comandos do plano de contas. Toda escrita relê as categorias da Company
 * (inclusive os seletores de Lançamentos e o onboarding). Renomear,
 * reclassificar e excluir também relêem lançamentos e relatórios, que
 * mostram nome e classificação das categorias. Sem retry automático.
 */
export function useFinancialCategoryMutations(companyId: string) {
    const queryClient = useQueryClient()

    const refreshCategories = () =>
        queryClient.invalidateQueries({ queryKey: ["companies", companyId, "financial-categories"] })

    const refreshEverything = () =>
        Promise.all([refreshCategories(), refreshCompanyFinancialData(queryClient, companyId)])

    return {
        createGroup: useMutation({
            mutationFn: (group: { direction: FinancialCategoryDirection, name: string }) =>
                createFinancialCategoryGroup(companyId, group),
            retry: false,
            onSuccess: refreshCategories,
        }),
        createSubcategory: useMutation({
            mutationFn: ({ group, name }: { group: FinancialCategory, name: string }) =>
                createFinancialSubcategory(companyId, group, name),
            retry: false,
            onSuccess: refreshCategories,
        }),
        rename: useMutation({
            mutationFn: ({ category, name }: { category: FinancialCategory, name: string }) =>
                renameFinancialCategory(companyId, category, name),
            retry: false,
            onSuccess: refreshEverything,
        }),
        classify: useMutation({
            mutationFn: ({ category, classification }: { category: FinancialCategory, classification: FinancialCategoryClassification }) =>
                updateFinancialCategoryClassification(companyId, category, classification),
            retry: false,
            onSuccess: refreshEverything,
        }),
        remove: useMutation({
            mutationFn: (category: FinancialCategory) => deleteFinancialCategory(companyId, category),
            retry: false,
            onSuccess: refreshEverything,
        }),
    }
}
