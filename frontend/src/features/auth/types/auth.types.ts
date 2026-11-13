export type PlatformPermission =
    | "ACCOUNTING_FIRM_CREATE"
    | "ACCOUNTING_FIRM_VIEW"
    | "UNASSIGNED_COMPANY_ASSIGN"
    | "PLATFORM_USER_MANAGE"
    | "SYSTEM_ADMIN"

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
    | "ACCOUNTING_MOVEMENT_VIEW"
    | "ACCOUNT_CLASSIFICATION_VIEW"
    | "BANK_ACCOUNT_VIEW"
    | "BANK_ACCOUNT_MANAGE"
    | "BUSINESS_PARTNER_VIEW"
    | "BUSINESS_PARTNER_MANAGE"
    | "RESULT_CENTER_VIEW"
    | "RESULT_CENTER_MANAGE"
    | "FINANCIAL_CATEGORY_VIEW"
    | "FINANCIAL_CATEGORY_MANAGE"
    /** Estrutura do plano (aplicar padrão, criar, renomear, excluir), sem classificação (#331). */
    | "FINANCIAL_CATEGORY_STRUCTURE_MANAGE"
    | "FINANCIAL_ENTRY_VIEW"
    | "FINANCIAL_ENTRY_MANAGE"
    | "FINANCIAL_PERIOD_VIEW"
    | "FINANCIAL_PERIOD_CLOSE"
    | "FINANCIAL_PERIOD_REOPEN"
    | "PLANNING_VIEW"
    | "PLANNING_MANAGE"
    | "CONTRACT_VIEW"
    | "CONTRACT_MANAGE"
    | "CRM_VIEW"
    | "CRM_MANAGE"

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
    platformPermissions?: PlatformPermission[]
    companies: CompanyAccess[]
}

export interface LoginRequest {
    email: string
    password: string
}
export function hasPlatformAccess(user: CurrentUser, permission: PlatformPermission): boolean {
    const assigned = user.platformPermissions ?? []
    return assigned.includes("SYSTEM_ADMIN") || assigned.includes(permission)
}
