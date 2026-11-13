import {
    useMemo,
    useState,
    type ReactNode,
} from "react"
import type { CompanyPermission, CurrentUser } from "../auth/types/auth.types"
import { CompanyAccessContext } from "./company-access-context"
import { canUseCompanyPermission } from "./company-permission-access"

interface CompanyAccessProviderProps {
    user: CurrentUser
    children: ReactNode
}

export function CompanyAccessProvider({
    user,
    children,
}: CompanyAccessProviderProps) {
    const [
        selectedCompanyId,
        setSelectedCompanyId,
    ] = useState<string | null>(
        user.companies[0]?.id ?? null,
    )

    const effectiveSelectedCompanyId =
        selectedCompanyId !== null &&
        user.companies.some(
            (company) => company.id === selectedCompanyId,
        )
            ? selectedCompanyId
            : user.companies[0]?.id ?? null

    const selectedCompany = useMemo(
        () =>
            user.companies.find(
                (company) =>
                    company.id === effectiveSelectedCompanyId,
            ) ?? null,
        [
            effectiveSelectedCompanyId,
            user.companies,
        ],
    )

    function hasPermission(
        permission: CompanyPermission,
    ) {
        return canUseCompanyPermission(
            user,
            selectedCompany,
            permission,
        )
    }

    return (
        <CompanyAccessContext.Provider
            value={{
                user,
                selectedCompany,
                selectCompany:
                    setSelectedCompanyId,
                hasPermission,
            }}
        >
            {children}
        </CompanyAccessContext.Provider>
    )
}
