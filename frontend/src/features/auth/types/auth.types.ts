export type CompanyAccessRole =
    | "CUSTOM"
    | "ADMIN"
    | "ACCOUNTANT"
    | "CLIENT"
    | "VIEWER"

export type CompanyPermission =
    | "DASHBOARD_VIEW"
    | "DRE_VIEW"
    | "BALANCE_SHEET_VIEW"
    | "CASH_FLOW_VIEW"
    | "INDICATORS_VIEW"
    | "ACCOUNT_CLASSIFICATION_MANAGE"
    | "MANUAL_ENTRY_MANAGE"
    | "QUESTOR_SYNC"

export type CostCenterScope = "ALL" | "RESTRICTED"

export interface CompanyAccess {
    id: string
    name: string
    role: CompanyAccessRole
    permissions: CompanyPermission[]
    costCenterScope: CostCenterScope
}

export interface CurrentUser {
    id: string
    name: string
    email: string
    companies: CompanyAccess[]
}

export interface LoginRequest {
    email: string
    password: string
}