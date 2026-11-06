import {
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react"
import type { CompanyPermission, CurrentUser } from "../auth/types/auth.types"
import { CompanyAccessContext } from "./company-access-context"

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

    useEffect(() => {
        const stillAvailable =
            selectedCompanyId !== null &&
            user.companies.some(
                (company) => company.id === selectedCompanyId,
            )

        if (!stillAvailable) {
            setSelectedCompanyId(
                user.companies[0]?.id ?? null,
            )
        }
    }, [selectedCompanyId, user.companies])

    const selectedCompany = useMemo(
        () =>
            user.companies.find(
                (company) =>
                    company.id === selectedCompanyId,
            ) ?? null,
        [
            selectedCompanyId,
            user.companies,
        ],
    )

    function hasPermission(
        permission: CompanyPermission,
    ) {
        return (
            selectedCompany?.permissions.includes(
                permission,
            ) ?? false
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