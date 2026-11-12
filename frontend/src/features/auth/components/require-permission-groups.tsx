import type { ReactNode } from "react"
import { Navigate } from "react-router"
import type { CompanyPermission } from "../types/auth.types"
import { useCompanyAccess } from "../../companies/company-access-context"

interface RequirePermissionGroupsProps {
    /** Cada grupo exige ao menos uma permissão; todos os grupos devem passar. */
    groups: CompanyPermission[][]
    children: ReactNode
}

/**
 * UX guard para contratos que combinam famílias de permissão.
 * A autorização definitiva continua no backend.
 */
export function RequirePermissionGroups({
    groups,
    children,
}: RequirePermissionGroupsProps) {
    const { hasPermission } = useCompanyAccess()

    if (!groups.every((group) => group.some(hasPermission))) {
        return <Navigate to="/dashboard" replace />
    }

    return children
}
