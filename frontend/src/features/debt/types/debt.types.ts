import type { PageResponse } from "../../../lib/api/pagination.types.ts"

export type DebtOrigin =
    | "BANK"
    | "SUPPLIER"
    | "TAX"
    | "THIRD_PARTY"

export type DebtAmortizationSystem =
    | "SAC"
    | "FIXED"

export type DebtRateInputPeriod =
    | "MONTHLY"
    | "ANNUAL"
    | "IMPLIED"

export interface DebtAgreement {
    id: string
    companyId: string
    businessPartnerId: string | null
    businessPartnerName: string | null
    origin: DebtOrigin
    description: string
    amortizationSystem: DebtAmortizationSystem
    principalAmount: string
    installmentCount: number
    inputRate: string | null
    inputRatePeriod: DebtRateInputPeriod
    fixedInstallmentAmount: string | null
    effectiveMonthlyRate: string
    firstDueDate: string
    version: number
    createdAt: string
    updatedAt: string
}

export type DebtAgreementPage = PageResponse<DebtAgreement>

export interface DebtPartnerOption {
    id: string
    name: string
}

export type DebtPartnerOptionPage =
    PageResponse<DebtPartnerOption>

export interface DebtInstallment {
    id: string
    installmentNumber: number
    dueDate: string
    amortizationAmount: string
    interestAmount: string
    installmentAmount: string
    balanceAfter: string
}

export interface DebtAgreementValues {
    businessPartnerId: string | null
    origin: DebtOrigin
    description: string
    amortizationSystem: DebtAmortizationSystem
    principalAmount: string
    installmentCount: number
    inputRate: string | null
    inputRatePeriod: DebtRateInputPeriod
    fixedInstallmentAmount: string | null
    firstDueDate: string
}
