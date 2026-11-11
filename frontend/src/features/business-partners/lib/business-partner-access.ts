import type { CompanyPermission } from "../../auth/types/auth.types.ts"

export const BUSINESS_PARTNER_READ_PERMISSIONS: CompanyPermission[] = [
    "BUSINESS_PARTNER_VIEW",
    "BUSINESS_PARTNER_MANAGE",
]

/** Incluir, editar e excluir exigem BUSINESS_PARTNER_MANAGE (só UX; o backend autoriza). */
export function canManageBusinessPartners(
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return hasPermission("BUSINESS_PARTNER_MANAGE")
}
