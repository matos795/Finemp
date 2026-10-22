import {
    createContext,
    useContext,
} from "react"
import type { CompanyAccess, CompanyPermission, CurrentUser } from "../auth/types/auth.types"

export interface CompanyAccessContextValue {
    user: CurrentUser
    selectedCompany: CompanyAccess | null
    selectCompany: (companyId: string) => void
    hasPermission: (
        permission: CompanyPermission,
    ) => boolean
}

export const CompanyAccessContext =
    createContext<CompanyAccessContextValue | null>(
        null,
    )

export function useCompanyAccess() {
    const context = useContext(
        CompanyAccessContext,
    )

    if (!context) {
        throw new Error(
            "useCompanyAccess must be used inside CompanyAccessProvider",
        )
    }

    return context
}