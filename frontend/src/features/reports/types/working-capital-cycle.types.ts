export type WorkingCapitalTermBand =
    | "CASH"
    | "DAYS_1_30"
    | "DAYS_31_60"
    | "DAYS_61_90"
    | "OVER_90"

export interface WorkingCapitalTermBandTotal {
    band: WorkingCapitalTermBand
    count: number
    amount: string
}

export interface WorkingCapitalPartnerTermMetrics {
    partnerId: string | null
    partnerName: string
    volume: string
    grantedDays: string | null
    realDays: string | null
    averageDelayDays: string | null
    overdueOpenAmount: string
}

export interface WorkingCapitalCycleSide {
    volume: string
    dailyVolume: string
    issuedCount: number
    grantedDays: string | null
    realDays: string | null
    averageDelayDays: string | null
    punctualityPercent: string | null
    earlyPayments: {
        count: number
        amount: string
        averageDays: string | null
    }
    interest: string
    discounts: string
    cashAmount: string
    openAmount: string
    overdueAmount: string
    openDelayDays: string | null
    balanceDays: string | null
    bands: WorkingCapitalTermBandTotal[]
    partners: WorkingCapitalPartnerTermMetrics[]
}

export interface WorkingCapitalCycleReport {
    from: string
    to: string
    asOf: string
    daysInPeriod: number
    pmeDays: number
    receivables: WorkingCapitalCycleSide
    payables: WorkingCapitalCycleSide
    pmrDays: string | null
    pmpDays: string | null
    operatingCycleDays: string | null
    financialCycleDays: string | null
    workingCapitalForCycle: string | null
}
