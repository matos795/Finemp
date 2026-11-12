import { apiRequest } from "../../../lib/api/api-client.ts"
import type { PageResponse } from "../../../lib/api/pagination.types.ts"
import type { StandardPlanResult } from "../../financial-entries/types/financial-catalog.types.ts"
import { financialCategoryListQuery } from "../lib/financial-category-form.ts"
import type {
    FinancialCategory,
    FinancialCategoryClassification,
    FinancialCategoryDirection,
    FinancialCategoryFilter,
} from "../types/financial-category.types.ts"

/*
 * Um comando por operação de negócio. Estrutura (plano padrão, criar,
 * renomear, excluir) e classificação (PUT completo) ficam separadas porque
 * exigem permissões diferentes no backend (#331); não há "saveCategory".
 */

function categoriesPath(companyId: string, suffix = "") {
    return `/api/companies/${encodeURIComponent(companyId)}/financial-categories${suffix}`
}

function categoryPath(companyId: string, categoryId: string, suffix = "") {
    return categoriesPath(companyId, `/${encodeURIComponent(categoryId)}${suffix}`)
}

/** GET paginado, com busca e filtros aplicados no servidor. */
export function listFinancialCategories(
    companyId: string,
    filter: FinancialCategoryFilter,
    page: number,
): Promise<PageResponse<FinancialCategory>> {
    return apiRequest<PageResponse<FinancialCategory>>(
        `${categoriesPath(companyId)}?${financialCategoryListQuery(filter, page)}`,
    )
}

export function getFinancialCategory(companyId: string, categoryId: string): Promise<FinancialCategory> {
    return apiRequest<FinancialCategory>(categoryPath(companyId, categoryId))
}

/**
 * Cria o plano homologado FINEMP numa Company sem nenhuma categoria. O
 * backend recusa com 409 PLAN_NOT_EMPTY se já houver catálogo.
 */
export function applyStandardFinancialCategoryPlan(companyId: string): Promise<StandardPlanResult> {
    return apiRequest<StandardPlanResult>(categoriesPath(companyId, "/standard-plan"), { method: "POST" })
}

/** O backend gera o código e uma subcategoria inicial. */
export function createFinancialCategoryGroup(
    companyId: string,
    group: { direction: FinancialCategoryDirection, name: string },
): Promise<FinancialCategory> {
    return apiRequest<FinancialCategory>(categoriesPath(companyId, "/groups"), {
        method: "POST",
        body: JSON.stringify({ direction: group.direction, name: group.name.trim() }),
    })
}

/** Só o nome; código e herança de classificação ficam com o backend. */
export function createFinancialSubcategory(
    companyId: string,
    group: Pick<FinancialCategory, "id">,
    name: string,
): Promise<FinancialCategory> {
    return apiRequest<FinancialCategory>(categoryPath(companyId, group.id, "/subcategories"), {
        method: "POST",
        body: JSON.stringify({ name: name.trim() }),
    })
}

/** Comando dedicado: só nome + versão; nunca toca a classificação. */
export function renameFinancialCategory(
    companyId: string,
    category: Pick<FinancialCategory, "id" | "version">,
    name: string,
): Promise<FinancialCategory> {
    return apiRequest<FinancialCategory>(categoryPath(companyId, category.id, "/rename"), {
        method: "POST",
        body: JSON.stringify({ name: name.trim(), expectedVersion: category.version }),
    })
}

/**
 * PUT de classificação (somente FINANCIAL_CATEGORY_MANAGE). O contrato
 * exige o nome: vai o nome atual recebido da API, sem edição — renomear é
 * outro comando. A versão também é a recebida da API.
 */
export function updateFinancialCategoryClassification(
    companyId: string,
    category: Pick<FinancialCategory, "id" | "name" | "version">,
    classification: FinancialCategoryClassification,
): Promise<FinancialCategory> {
    return apiRequest<FinancialCategory>(categoryPath(companyId, category.id), {
        method: "PUT",
        body: JSON.stringify({
            name: category.name,
            cashActivity: classification.cashActivity,
            dreLine: classification.dreLine,
            costBehavior: classification.costBehavior,
            expectedVersion: category.version,
        }),
    })
}

/** Exclui categoria sem uso (grupo leva as subcategorias). Sem "forçar". */
export function deleteFinancialCategory(
    companyId: string,
    category: Pick<FinancialCategory, "id" | "version">,
): Promise<void> {
    return apiRequest<void>(`${categoryPath(companyId, category.id)}?version=${category.version}`, { method: "DELETE" })
}
