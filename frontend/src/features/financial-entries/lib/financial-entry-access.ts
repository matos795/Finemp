import type { CompanyPermission } from "../../auth/types/auth.types.ts"

/** Leitura: VIEW ou MANAGE (só UX; o backend autoriza de novo). */
export const FINANCIAL_ENTRY_READ_PERMISSIONS: CompanyPermission[] = [
    "FINANCIAL_ENTRY_VIEW",
    "FINANCIAL_ENTRY_MANAGE",
]

/**
 * Botões de escrita ("+ Entrada", "+ Saída", "Editar") só aparecem com
 * FINANCIAL_ENTRY_MANAGE. Não depende do papel (CLIENT, ACCOUNTANT…).
 */
export function canManageFinancialEntries(
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return hasPermission("FINANCIAL_ENTRY_MANAGE")
}
