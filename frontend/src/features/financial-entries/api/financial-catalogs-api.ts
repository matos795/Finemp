import { apiRequest } from "../../../lib/api/api-client.ts"
import type { PageResponse } from "../../../lib/api/pagination.types.ts"
import type { FinancialDirection } from "../types/financial-entry.types.ts"
import type {
    BusinessPartnerOption,
    FinancialCategoryOption,
    ResultCenterOption,
    ResultCenterSettings,
} from "../types/financial-catalog.types.ts"

/** Maior página aceita pelos catálogos financeiros. */
export const CATALOG_PAGE_SIZE = 100

function companyPath(companyId: string, resource: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/${resource}`
}

/**
 * Percorre todas as páginas de um catálogo paginado no servidor. Os
 * seletores precisam permitir escolher qualquer item; não há endpoint de
 * busca para isso, então o frontend só concatena as páginas devolvidas.
 */
export async function loadAllPages<T>(
    loadPage: (page: number) => Promise<PageResponse<T>>,
): Promise<T[]> {
    const first = await loadPage(0)

    if (first.totalPages <= 1) {
        return first.items
    }

    const rest = await Promise.all(
        Array.from({ length: first.totalPages - 1 }, (_, index) => loadPage(index + 1)),
    )

    return [...first.items, ...rest.flatMap((page) => page.items)]
}

function pageParams(page: number, extra: Record<string, string> = {}) {
    return new URLSearchParams({
        page: String(page),
        size: String(CATALOG_PAGE_SIZE),
        ...extra,
    })
}

/** `GET …/financial-categories`: grupos e subcategorias, em ordem hierárquica. */
export function listAllFinancialCategories(
    companyId: string,
    direction?: FinancialDirection,
): Promise<FinancialCategoryOption[]> {
    return loadAllPages((page) =>
        apiRequest<PageResponse<FinancialCategoryOption>>(
            `${companyPath(companyId, "financial-categories")}?${pageParams(page, direction ? { direction } : {})}`,
        ),
    )
}

/**
 * `GET …/business-partners?active=true`. O papel é filtrado no servidor:
 * CUSTOMER e SUPPLIER já incluem BOTH.
 */
export function listActiveBusinessPartners(
    companyId: string,
    role: "CUSTOMER" | "SUPPLIER",
): Promise<BusinessPartnerOption[]> {
    return loadAllPages((page) =>
        apiRequest<PageResponse<BusinessPartnerOption>>(
            `${companyPath(companyId, "business-partners")}?${pageParams(page, { role, active: "true" })}`,
        ),
    )
}

/** `GET …/result-centers`: catálogo completo (ativos e inativos). */
export function listAllResultCenters(
    companyId: string,
): Promise<ResultCenterOption[]> {
    return loadAllPages((page) =>
        apiRequest<PageResponse<ResultCenterOption>>(
            `${companyPath(companyId, "result-centers")}?${pageParams(page)}`,
        ),
    )
}

/** `GET …/result-centers/settings`: se a Empresa exige centro no lançamento. */
export function getResultCenterSettings(
    companyId: string,
): Promise<ResultCenterSettings> {
    return apiRequest<ResultCenterSettings>(
        companyPath(companyId, "result-centers/settings"),
    )
}
