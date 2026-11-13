import type { PageResponse } from "../../../lib/api/pagination.types.ts"

export type ContractDirection = "INCOME" | "EXPENSE"

export interface Contract {
    id: string
    companyId: string
    businessPartnerId: string
    businessPartnerName: string
    direction: ContractDirection
    description: string
    startDate: string
    endDate: string | null
    billingDay: number
    monthlyAmount: string
    active: boolean
    version: number
    createdAt: string
    updatedAt: string
}

export type ContractPage = PageResponse<Contract>

export interface ContractPartnerOption {
    id: string
    name: string
}

export type ContractPartnerOptionPage = PageResponse<ContractPartnerOption>

export interface ContractFilter {
    search: string
    active: "true" | "false" | ""
}

export interface ContractValues {
    businessPartnerId: string
    direction: ContractDirection
    description: string
    startDate: string
    endDate: string | null
    billingDay: number
    monthlyAmount: string
    active: boolean
}

export const defaultContractFilter: ContractFilter = {
    search: "",
    active: "true",
}
