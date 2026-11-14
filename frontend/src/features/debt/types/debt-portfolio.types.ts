import type { PageResponse } from "../../../lib/api/pagination.types.ts"

export interface DebtPortfolioOriginSummary {
    origin: "BANK" | "SUPPLIER" | "TAX" | "THIRD_PARTY"
    agreementCount: number
    principalAmount: string
    scheduledAmount: string
    interestAmount: string
}

export interface DebtPortfolioOverview {
    companyId: string
    agreementCount: number
    principalAmount: string
    scheduledAmount: string
    interestAmount: string
    byOrigin: DebtPortfolioOriginSummary[]
}

export interface DebtPortfolioScheduleItem {
    id: string
    debtId: string
    origin: "BANK" | "SUPPLIER" | "TAX" | "THIRD_PARTY"
    description: string
    creditorName: string | null
    installmentNumber: number
    dueDate: string
    amortizationAmount: string
    interestAmount: string
    installmentAmount: string
    balanceAfter: string
}

export type DebtPortfolioSchedulePage =
    PageResponse<DebtPortfolioScheduleItem>
