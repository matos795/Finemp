import type { ReactNode } from "react"
import { Navigate } from "react-router"
import type { CompanyPermission } from "../types/auth.types"
import { useCompanyAccess } from "../../companies/company-access-context"

interface RequireAllPermissionsProps {
    permissions: CompanyPermission[]
    children: ReactNode
}

/**
 * UX guard for screens whose read contract combines permission families.
 * Backend authorization remains authoritative.
 */
export function RequireAllPermissions({
    permissions,
    children,
}: RequireAllPermissionsProps) {
    const { hasPermission } = useCompanyAccess()

    if (!permissions.every(hasPermission)) {
        return <Navigate to="/dashboard" replace />
    }

    return children
}
