import type { PageResponse } from "../../../lib/api/pagination.types.ts"
import type {
    CashActivity,
    CostBehavior,
    FinancialCategory,
    FinancialCategoryClassification,
    FinancialCategoryDirection,
    FinancialCategoryFilter,
    FinancialCategoryKind,
    OperationalDreLine,
} from "../types/financial-category.types.ts"

export const FINANCIAL_CATEGORY_PAGE_SIZE = 50

/** Rótulos do protótipo (ATIV, DRE_LINHAS, CV); só apresentação. */
export const DIRECTION_LABELS: Record<FinancialCategoryDirection, string> = { INCOME: "Receita", EXPENSE: "Despesa" }
export const DIRECTION_SECTION_LABELS: Record<FinancialCategoryDirection, string> = {
    INCOME: "Receitas (entradas)",
    EXPENSE: "Despesas (saídas)",
}
export const KIND_LABELS: Record<FinancialCategoryKind, string> = { GROUP: "Grupo", SUBCATEGORY: "Subcategoria" }
export const CASH_ACTIVITY_LABELS: Record<CashActivity, string> = {
    OPERATING: "Operacional",
    INVESTING: "Investimento",
    FINANCING: "Financiamento",
}
export const DRE_LINE_LABELS: Record<OperationalDreLine, string> = {
    REC: "Receita bruta",
    DED: "(−) Deduções e impostos sobre vendas",
    CUS: "(−) Custos das mercadorias, produtos e serviços",
    DCOM: "Despesas comerciais",
    DADM: "Despesas administrativas",
    DPES: "Despesas com pessoal",
    DTRI: "Impostos e taxas",
    OUT: "Outras receitas e despesas operacionais",
    DEP: "(−) Depreciação e amortização",
    RFIN: "Receitas financeiras",
    DFIN: "Despesas financeiras",
    IR: "(−) IR e CSLL",
    NAO: "Não entra na DRE (investimento, financiamento, sócios)",
}
export const COST_BEHAVIOR_LABELS: Record<CostBehavior, string> = { FIXED: "Fixo", VARIABLE: "Variável" }

export const defaultFinancialCategoryFilter: FinancialCategoryFilter = { search: "", direction: "", kind: "" }

/** Parâmetros do GET: paginação, busca e filtros vão para o servidor. */
export function financialCategoryListQuery(filter: FinancialCategoryFilter, page: number) {
    const params = new URLSearchParams({ page: String(page), size: String(FINANCIAL_CATEGORY_PAGE_SIZE) })
    const search = filter.search.trim()

    if (search) {
        params.set("search", search)
    }

    if (filter.direction) {
        params.set("direction", filter.direction)
    }

    if (filter.kind) {
        params.set("kind", filter.kind)
    }

    return params
}

/** Catálogo realmente vazio: primeira página, sem busca nem filtros. */
export function isUnfilteredEmptyCatalog(
    filter: FinancialCategoryFilter,
    page: number,
    data: PageResponse<FinancialCategory> | undefined,
) {
    return data !== undefined && page === 0 && data.totalItems === 0 &&
        !filter.search.trim() && !filter.direction && !filter.kind
}

/** Nome para criar ou renomear (o backend também valida). */
export function validateCategoryName(name: string): string | undefined {
    const trimmed = name.trim()

    if (!trimmed) {
        return "Informe o nome."
    }

    if (trimmed.length > 255) {
        return "Use no máximo 255 caracteres."
    }

    return undefined
}

/**
 * Campos de classificação sem valor próprio numa subcategoria: a tela
 * marca "do grupo" e mostra o efetivo que veio da API (não recalcula).
 */
export function inheritedFields(category: FinancialCategory): (keyof FinancialCategoryClassification)[] {
    if (category.kind !== "SUBCATEGORY") {
        return []
    }

    return (["cashActivity", "dreLine", "costBehavior"] as const).filter((field) => category[field] === null)
}

/** Grupo sem fixo/variável próprio: o backend usa o padrão da direção. */
export function usesDirectionDefaultCost(category: FinancialCategory) {
    return category.kind === "GROUP" && category.costBehavior === null
}

/** Valores do formulário de classificação ("" = sem valor próprio). */
export interface ClassificationForm {
    cashActivity: CashActivity | ""
    dreLine: OperationalDreLine | ""
    costBehavior: CostBehavior | ""
}

export type ClassificationFormErrors = Partial<Record<keyof ClassificationForm, string>>

/** Parte dos valores raw; `effective` nunca alimenta o formulário. */
export function classificationFormFrom(category: FinancialCategory): ClassificationForm {
    return {
        cashActivity: category.cashActivity ?? "",
        dreLine: category.dreLine ?? "",
        costBehavior: category.costBehavior ?? "",
    }
}

export function toClassificationRequest(form: ClassificationForm): FinancialCategoryClassification {
    return {
        cashActivity: form.cashActivity || null,
        dreLine: form.dreLine || null,
        costBehavior: form.costBehavior || null,
    }
}

/** Mesma exigência do PUT: no grupo, atividade e DRE são obrigatórias. */
export function validateClassificationForm(
    category: Pick<FinancialCategory, "kind">,
    form: ClassificationForm,
): ClassificationFormErrors {
    const errors: ClassificationFormErrors = {}

    if (category.kind === "GROUP" && !form.cashActivity) {
        errors.cashActivity = "Escolha a atividade do grupo."
    }

    if (category.kind === "GROUP" && !form.dreLine) {
        errors.dreLine = "Escolha a linha da DRE do grupo."
    }

    return errors
}
