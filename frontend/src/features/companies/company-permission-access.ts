import type {
    CompanyAccess,
    CompanyPermission,
    CurrentUser,
} from "../auth/types/auth.types"

export function canUseCompanyPermission(
    user: CurrentUser,
    company: CompanyAccess | null,
    permission: CompanyPermission,
): boolean {
    if (user.platformPermissions?.includes("SYSTEM_ADMIN")) {
        return true
    }

    return company?.permissions.includes(permission) ?? false
}
