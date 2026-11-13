import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    Opportunity,
    OpportunityStatus,
    OpportunitySummary,
    OpportunityValues,
    PageResponse,
    SalesActivity,
    SalesActivityValues,
    SalesIndicators,
    SalesPartnerOption,
    SalesProfile,
} from "../types/sales.types.ts"

const PAGE_SIZE = 50
const companyPath = (companyId: string) => `/api/companies/${companyId}`

export async function listOpportunities(
    companyId: string,
    filter: {
        search?: string
        responsible?: string
        stage?: string
        status?: OpportunityStatus | ""
    },
    page = 0,
    signal?: AbortSignal,
): Promise<PageResponse<OpportunitySummary>> {
    const params = new URLSearchParams({
        page: String(page),
        size: String(PAGE_SIZE),
    })
    if (filter.search?.trim()) params.set("search", filter.search.trim())
    if (filter.responsible?.trim()) params.set("responsible", filter.responsible.trim())
    if (filter.stage?.trim()) params.set("stage", filter.stage.trim())
    if (filter.status) params.set("status", filter.status)

    return apiRequest(
        `${companyPath(companyId)}/opportunities?${params}`,
        { signal, cache: "no-store" },
    )
}

export function getOpportunity(
    companyId: string,
    opportunityId: string,
): Promise<Opportunity> {
    return apiRequest(
        `${companyPath(companyId)}/opportunities/${opportunityId}`,
        { cache: "no-store" },
    )
}

export function createOpportunity(
    companyId: string,
    values: OpportunityValues,
): Promise<Opportunity> {
    return apiRequest(`${companyPath(companyId)}/opportunities`, {
        method: "POST",
        body: JSON.stringify({ ...values, expectedVersion: null }),
    })
}

export function updateOpportunity(
    companyId: string,
    opportunity: OpportunitySummary,
    values: OpportunityValues,
): Promise<Opportunity> {
    return apiRequest(
        `${companyPath(companyId)}/opportunities/${opportunity.id}`,
        {
            method: "PUT",
            body: JSON.stringify({
                ...values,
                expectedVersion: opportunity.version,
            }),
        },
    )
}

export function moveOpportunityStage(
    companyId: string,
    opportunity: OpportunitySummary,
    stage: string,
): Promise<Opportunity> {
    return apiRequest(
        `${companyPath(companyId)}/opportunities/${opportunity.id}/stage`,
        {
            method: "POST",
            body: JSON.stringify({
                stage,
                expectedVersion: opportunity.version,
            }),
        },
    )
}

export function changeOpportunityStatus(
    companyId: string,
    opportunity: OpportunitySummary,
    status: OpportunityStatus,
): Promise<Opportunity> {
    return apiRequest(
        `${companyPath(companyId)}/opportunities/${opportunity.id}/status`,
        {
            method: "POST",
            body: JSON.stringify({
                status,
                expectedVersion: opportunity.version,
            }),
        },
    )
}

export async function listSalesActivities(
    companyId: string,
    filter: { search?: string; completed?: "" | "true" | "false" },
    page = 0,
    signal?: AbortSignal,
): Promise<PageResponse<SalesActivity>> {
    const params = new URLSearchParams({
        page: String(page),
        size: String(PAGE_SIZE),
    })
    if (filter.search?.trim()) params.set("search", filter.search.trim())
    if (filter.completed) params.set("completed", filter.completed)

    return apiRequest(
        `${companyPath(companyId)}/sales-activities?${params}`,
        { signal, cache: "no-store" },
    )
}

export function createSalesActivity(
    companyId: string,
    values: SalesActivityValues,
): Promise<SalesActivity> {
    return apiRequest(`${companyPath(companyId)}/sales-activities`, {
        method: "POST",
        body: JSON.stringify({ ...values, expectedVersion: null }),
    })
}

export function changeSalesActivityCompletion(
    companyId: string,
    activity: SalesActivity,
    completed: boolean,
): Promise<SalesActivity> {
    return apiRequest(
        `${companyPath(companyId)}/sales-activities/${activity.id}/completion`,
        {
            method: "POST",
            body: JSON.stringify({
                completed,
                expectedVersion: activity.version,
            }),
        },
    )
}

export async function listSalesPartnerOptions(
    companyId: string,
    search: string,
    signal?: AbortSignal,
    includeInactive = false,
): Promise<PageResponse<SalesPartnerOption>> {
    const params = new URLSearchParams({
        page: "0",
        size: String(PAGE_SIZE),
    })
    if (search.trim()) params.set("search", search.trim())
    if (includeInactive) params.set("includeInactive", "true")

    return apiRequest(
        `${companyPath(companyId)}/sales/partners?${params}`,
        { signal, cache: "no-store" },
    )
}

export function getSalesProfile(
    companyId: string,
    partnerId: string,
): Promise<SalesProfile> {
    return apiRequest(
        `${companyPath(companyId)}/sales/partners/${partnerId}/profile`,
        { cache: "no-store" },
    )
}

export function getSalesIndicators(companyId: string): Promise<SalesIndicators> {
    return apiRequest(
        `${companyPath(companyId)}/sales/indicators`,
        { cache: "no-store" },
    )
}
