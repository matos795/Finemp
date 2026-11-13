export type OpportunityStatus = "OPEN" | "WON" | "LOST"
export type SalesActivityDueStatus = "OVERDUE" | "TODAY" | "UPCOMING" | "COMPLETED"

export interface PageResponse<T> {
    items: T[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export interface OpportunitySummary {
    id: string
    businessPartnerId: string | null
    businessPartnerName: string | null
    title: string
    stage: string
    monthlyAmount: string
    probability: number
    responsible: string
    expectedCloseOn: string | null
    status: OpportunityStatus
    version: number
    createdAt: string
    updatedAt: string
}

export interface Opportunity extends OpportunitySummary {
    companyId: string
    notes: string | null
    wonAt: string | null
    lostAt: string | null
    stageHistory: Array<{ stage: string; recordedAt: string }>
}

export interface OpportunityValues {
    businessPartnerId: string | null
    title: string
    stage: string
    monthlyAmount: string
    probability: number
    responsible: string
    expectedCloseOn: string | null
    notes: string | null
}

export interface SalesActivity {
    id: string
    companyId: string
    opportunityId: string | null
    opportunityTitle: string | null
    businessPartnerId: string | null
    businessPartnerName: string | null
    subject: string
    responsible: string
    dueOn: string
    notes: string | null
    completedAt: string | null
    dueStatus: SalesActivityDueStatus
    version: number
    createdAt: string
    updatedAt: string
}

export interface SalesActivityValues {
    opportunityId: string | null
    businessPartnerId: string | null
    subject: string
    responsible: string
    dueOn: string
    notes: string | null
}

export interface SalesPartnerOption {
    id: string
    name: string
}

export interface SalesProfile {
    businessPartnerId: string
    businessPartnerName: string
    active: boolean
    openOpportunities: number
    wonOpportunities: number
    lostOpportunities: number
    openActivities: number
    overdueActivities: number
    asOf: string
}

export interface SalesIndicators {
    wonCount: number
    lostCount: number
    conversionRate: string
    averageWonTicket: string
    averageSalesCycleDays: string
    weightedOpenPipeline: string
    annualizedWonValue: string
    forecast: Array<{ month: string; weightedAmount: string }>
}
