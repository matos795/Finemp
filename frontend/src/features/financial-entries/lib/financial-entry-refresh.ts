/** O suficiente de um QueryClient para invalidar consultas. */
interface QueryInvalidator {
    invalidateQueries: (filters: { queryKey: readonly unknown[] }) => Promise<unknown>
}

/**
 * Depois de um comando financeiro (baixa, estorno), recarrega os
 * lançamentos e os relatórios (fluxo, aging, DRE) só da Company informada.
 */
export function refreshCompanyFinancialData(
    queryClient: QueryInvalidator,
    companyId: string,
) {
    return Promise.all([
        queryClient.invalidateQueries({ queryKey: ["companies", companyId, "financial-entries"] }),
        queryClient.invalidateQueries({ queryKey: ["companies", companyId, "financial-reports"] }),
    ])
}
