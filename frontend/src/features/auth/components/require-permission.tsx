import type {
    ReactNode,
} from "react"
import {
    Navigate,
} from "react-router"
import type { CompanyPermission } from "../types/auth.types"
import { useCompanyAccess } from "../../companies/company-access-context"

interface RequirePermissionProps {
    /**
     * Uma permissão ou uma lista em que qualquer uma basta.
     * Ocultar é só UX: a autorização continua no backend.
     */
    permission: CompanyPermission | CompanyPermission[]
    children: ReactNode
}

export function RequirePermission({
    permission,
    children,
}: RequirePermissionProps) {
    const { hasPermission } =
        useCompanyAccess()

    const permissions =
        Array.isArray(permission)
            ? permission
            : [permission]

    if (!permissions.some(hasPermission)) {
        return (
            <Navigate
                to="/dashboard"
                replace
            />
        )
    }

    return children
}