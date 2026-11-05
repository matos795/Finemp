import { apiRequest } from "../../../lib/api/api-client"
import type {
    AgingDirection,
    AgingScope,
    OpenTitlePartnerTotalsPage,
    OpenTitlesAging,
    OpenTitlesFilter,
    OpenTitlesPage,
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

function openTitlesParams(
    filter: OpenTitlesFilter,
    page: number,
    size: number,
) {
    const params = new URLSearchParams({
        direction: filter.direction,
        asOf: filter.asOf,
        scope: filter.scope,
        page: String(page),
        size: String(size),
    })

    if (filter.band) {
        params.set("band", filter.band)
    }

    return params
}

/** Grupo da lista: um parceiro ou os títulos sem parceiro. */
export type OpenTitlesPartnerSelector =
    | { partnerId: string }
    | { unassignedPartner: true }

/**
 * `GET …/financial-reports/open-titles` (listOpenTitlesAsOf).
 * Dias de atraso, faixa e ordem vêm do servidor.
 */
export async function listOpenTitles(
    companyId: string,
    filter: OpenTitlesFilter,
    page: number,
    size: number,
    partner?: OpenTitlesPartnerSelector,
): Promise<OpenTitlesPage> {
    const params = openTitlesParams(filter, page, size)

    if (partner && "partnerId" in partner) {
        params.set("partnerId", partner.partnerId)
    } else if (partner) {
        params.set("unassignedPartner", "true")
    }

    return apiRequest<OpenTitlesPage>(
        `${reportsPath(companyId)}/open-titles?${params}`,
        { cache: "no-store" },
    )
}

/**
 * `GET …/financial-reports/open-titles/by-partner`
 * (listOpenTitlePartnerTotals). Subtotais e contagem vêm do servidor.
 */
export async function listOpenTitlePartnerTotals(
    companyId: string,
    filter: OpenTitlesFilter,
    page: number,
    size: number,
): Promise<OpenTitlePartnerTotalsPage> {
    return apiRequest<OpenTitlePartnerTotalsPage>(
        `${reportsPath(companyId)}/open-titles/by-partner?${openTitlesParams(filter, page, size)}`,
        { cache: "no-store" },
    )
}
