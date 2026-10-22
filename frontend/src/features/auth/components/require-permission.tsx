import type {
    ReactNode,
} from "react"
import {
    Navigate,
} from "react-router"
import type { CompanyPermission } from "../types/auth.types"
import { useCompanyAccess } from "../../companies/company-access-context"

interface RequirePermissionProps {
    permission: CompanyPermission
    children: ReactNode
}

export function RequirePermission({
    permission,
    children,
}: RequirePermissionProps) {
    const { hasPermission } =
        useCompanyAccess()

    if (!hasPermission(permission)) {
        return (
            <Navigate
                to="/dashboard"
                replace
            />
        )
    }

    return children
}