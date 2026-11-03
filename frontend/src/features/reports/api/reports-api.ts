import { apiRequest } from "../../../lib/api/api-client"
import type {
    AgingDirection,
    AgingScope,
    OpenTitlesAging,
} from "../types/aging.types"

function reportsPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/financial-reports`
}

/**
 * `GET …/financial-reports/open-titles/aging` (getOpenTitlesAging).
 * Totais e faixas vêm prontos do backend; o frontend só exibe.
 */
export async function getOpenTitlesAging(
    companyId: string,
    direction: AgingDirection,
    asOf: string,
    scope: AgingScope,
): Promise<OpenTitlesAging> {
    const params = new URLSearchParams({ direction, asOf, scope })

    return apiRequest<OpenTitlesAging>(
        `${reportsPath(companyId)}/open-titles/aging?${params}`,
    )
}
