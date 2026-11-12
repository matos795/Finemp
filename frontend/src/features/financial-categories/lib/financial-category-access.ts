import type { CompanyPermission } from "../../auth/types/auth.types.ts"
import type { FinancialCategory } from "../types/financial-category.types.ts"

export const FINANCIAL_CATEGORY_READ_PERMISSIONS: CompanyPermission[] = [
    "FINANCIAL_CATEGORY_VIEW",
    "FINANCIAL_CATEGORY_STRUCTURE_MANAGE",
    "FINANCIAL_CATEGORY_MANAGE",
]

export interface FinancialCategoryCapabilities {
    canRead: boolean
    /** Plano padrão, criar, renomear e excluir sem uso. */
    canManageStructure: boolean
    /** Atividade de caixa, linha da DRE e fixo/variável (PUT completo). */
    canClassify: boolean
}

/**
 * O que a tela oferece, só por permissões explícitas da Company (#331).
 * É UX: o backend continua autorizando cada comando.
 */
export function financialCategoryCapabilities(
    hasPermission: (permission: CompanyPermission) => boolean,
): FinancialCategoryCapabilities {
    const canClassify = hasPermission("FINANCIAL_CATEGORY_MANAGE")
    const canManageStructure = canClassify || hasPermission("FINANCIAL_CATEGORY_STRUCTURE_MANAGE")

    return {
        canRead: canManageStructure || hasPermission("FINANCIAL_CATEGORY_VIEW"),
        canManageStructure,
        canClassify,
    }
}

export type FinancialCategoryAction = "create-subcategory" | "rename" | "classify" | "delete"

/** Ações de uma linha, na ordem em que aparecem. */
export function rowActions(
    category: Pick<FinancialCategory, "kind">,
    capabilities: FinancialCategoryCapabilities,
): FinancialCategoryAction[] {
    const actions: FinancialCategoryAction[] = []

    if (capabilities.canManageStructure && category.kind === "GROUP") {
        actions.push("create-subcategory")
    }

    if (capabilities.canManageStructure) {
        actions.push("rename")
    }

    if (capabilities.canClassify) {
        actions.push("classify")
    }

    if (capabilities.canManageStructure) {
        actions.push("delete")
    }

    return actions
}

/** Ações do estado vazio; sempre por clique, nunca automáticas. */
export function emptyCatalogActions(
    capabilities: FinancialCategoryCapabilities,
): ("apply-standard-plan" | "create-first-group")[] {
    return capabilities.canManageStructure ? ["apply-standard-plan", "create-first-group"] : []
}
