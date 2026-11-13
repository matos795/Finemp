import { apiRequest } from "../../../lib/api/api-client"
import type {
    AgingDirection,
    AgingScope,
    OpenTitlePartnerTotalsPage,
    OpenTitlesAging,
    OpenTitlesFilter,
    OpenTitlesPage,
} from "../types/aging.types"
import type { AccountBalancesReport } from "../types/account-balances.types"
import type { ReceivedPaymentsByMethodReport } from "../types/received-payments.types"
import type {
    DailyCashTotals,
    MonthlyCashCategoryTotalsPage,
    MonthlyCashDetailFilter,
    MonthlyCashTitlesPage,
    MonthlyCashTotals,
} from "../types/cash-totals.types"

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

/** `GET …/financial-reports/monthly-cash-totals?year=` (getMonthlyCashTotals). */
export async function getMonthlyCashTotals(
    companyId: string,
    year: number,
): Promise<MonthlyCashTotals> {
    const params = new URLSearchParams({ year: String(year) })

    return apiRequest<MonthlyCashTotals>(
        `${reportsPath(companyId)}/monthly-cash-totals?${params}`,
        { cache: "no-store" },
    )
}

/** `GET …/financial-reports/daily-cash-totals?month=YYYY-MM` (getDailyCashTotals). */
export async function getDailyCashTotals(
    companyId: string,
    month: string,
): Promise<DailyCashTotals> {
    const params = new URLSearchParams({ month })

    return apiRequest<DailyCashTotals>(
        `${reportsPath(companyId)}/daily-cash-totals?${params}`,
    )
}

function monthlyDetailParams(
    filter: MonthlyCashDetailFilter,
    page: number,
    size: number,
) {
    const params = new URLSearchParams({
        month: filter.month,
        direction: filter.direction,
        state: filter.state,
        page: String(page),
        size: String(size),
    })

    if (filter.activity) {
        params.set("activity", filter.activity)
    }

    if (filter.categoryId) {
        params.set("categoryId", filter.categoryId)
    }

    return params
}

/**
 * `GET …/monthly-cash-totals/by-category` (listMonthlyCashCategoryTotals).
 * Subtotais por categoria somados no servidor.
 */
export async function listMonthlyCashCategoryTotals(
    companyId: string,
    filter: Omit<MonthlyCashDetailFilter, "categoryId">,
    page: number,
    size: number,
): Promise<MonthlyCashCategoryTotalsPage> {
    return apiRequest<MonthlyCashCategoryTotalsPage>(
        `${reportsPath(companyId)}/monthly-cash-totals/by-category?${monthlyDetailParams(filter, page, size)}`,
        { cache: "no-store" },
    )
}

/** `GET …/monthly-cash-totals/titles` (listMonthlyCashTitles). */
export async function listMonthlyCashTitles(
    companyId: string,
    filter: MonthlyCashDetailFilter,
    page: number,
    size: number,
): Promise<MonthlyCashTitlesPage> {
    return apiRequest<MonthlyCashTitlesPage>(
        `${reportsPath(companyId)}/monthly-cash-totals/titles?${monthlyDetailParams(filter, page, size)}`,
        { cache: "no-store" },
    )
}


/** GET …/financial-reports/account-balances. Saldos já vêm derivados do backend. */
export async function getAccountBalances(
    companyId: string,
): Promise<AccountBalancesReport> {
    return apiRequest<AccountBalancesReport>(
        `${reportsPath(companyId)}/account-balances`,
        { cache: "no-store" },
    )
}

/** Receitas efetivamente baixadas, agrupadas pela forma persistida no lançamento. */
export async function getReceivedPaymentsByMethod(
    companyId: string,
    from: string,
    to: string,
): Promise<ReceivedPaymentsByMethodReport> {
    const params = new URLSearchParams({ from, to })

    return apiRequest<ReceivedPaymentsByMethodReport>(
        `${reportsPath(companyId)}/received-by-method?${params}`,
        { cache: "no-store" },
    )
}
