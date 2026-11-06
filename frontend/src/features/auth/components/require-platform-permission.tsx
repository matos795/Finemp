import type { ReactNode } from "react"
import { Navigate } from "react-router"
import type { PlatformPermission } from "../types/auth.types"
import { useCompanyAccess } from "../../companies/company-access-context"

interface RequirePlatformPermissionProps {
    permission: PlatformPermission | PlatformPermission[]
    children: ReactNode
}

/**
 * Controle de visibilidade do frontend para telas administrativas.
 * A autorização real continua obrigatoriamente no backend.
 */
export function RequirePlatformPermission({
    permission,
    children,
}: RequirePlatformPermissionProps) {
    const { user } = useCompanyAccess()

    const permissions =
        Array.isArray(permission)
            ? permission
            : [permission]

    const allowed = permissions.some((candidate) =>
        user.platformPermissions.includes(candidate),
    )

    if (!allowed) {
        return <Navigate to="/" replace />
    }

    return children
}
