import { useQuery } from "@tanstack/react-query"
import { useAllBankAccounts } from "../../bank-accounts/hooks/use-bank-accounts"
import {
    getResultCenterSettings,
    listActiveBusinessPartners,
    listAllFinancialCategories,
    listAllResultCenters,
} from "../api/financial-catalogs-api"
import type { FinancialDirection } from "../types/financial-entry.types"

/**
 * Catálogos usados pelos filtros e pelo formulário de lançamentos. Cada
 * um tem a permissão própria (BANK_ACCOUNT_VIEW, FINANCIAL_CATEGORY_VIEW,
 * RESULT_CENTER_VIEW); um 403 afeta só o seletor correspondente.
 */
export function useFinancialEntryCatalogs(
    companyId: string | undefined,
) {
    const bankAccounts = useAllBankAccounts(companyId)

    const categories = useQuery({
        queryKey: ["companies", companyId, "financial-categories", "all"],
        queryFn: () => listAllFinancialCategories(companyId!),
        enabled: Boolean(companyId),
    })

    const resultCenters = useQuery({
        queryKey: ["companies", companyId, "result-centers", "all"],
        queryFn: () => listAllResultCenters(companyId!),
        enabled: Boolean(companyId),
    })

    return { bankAccounts, categories, resultCenters }
}

/** Clientes (entrada) ou fornecedores (saída) ativos, para o formulário. */
export function useEntryPartners(
    companyId: string | undefined,
    direction: FinancialDirection,
    enabled: boolean,
) {
    const role = direction === "INCOME" ? "CUSTOMER" : "SUPPLIER"

    return useQuery({
        queryKey: ["companies", companyId, "business-partners", "active", role],
        queryFn: () => listActiveBusinessPartners(companyId!, role),
        enabled: enabled && Boolean(companyId),
    })
}

/** Se a empresa exige centro de resultado (o backend continua validando). */
export function useResultCenterSettings(
    companyId: string | undefined,
    enabled: boolean,
) {
    return useQuery({
        queryKey: ["companies", companyId, "result-centers", "settings"],
        queryFn: () => getResultCenterSettings(companyId!),
        enabled: enabled && Boolean(companyId),
        retry: false,
    })
}
