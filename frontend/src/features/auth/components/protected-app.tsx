import { Navigate, Outlet } from "react-router"
import { useCurrentUser } from "../hooks/use-current-user"
import { Skeleton } from "../../../components/ui/skeleton"
import { CompanyAccessProvider } from "../../companies/company-access-provider"

export function ProtectedApp() {
    const currentUser = useCurrentUser()

    if (currentUser.isLoading) {
        return (
            <div className="flex min-h-screen items-center justify-center">
                <div className="w-72 space-y-3">
                    <Skeleton className="h-5 w-32" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                </div>
            </div>
        )
    }

    if (!currentUser.data) {
        return (
            <Navigate
                to="/login"
                replace
            />
        )
    }

    return (
        <CompanyAccessProvider
            user={currentUser.data}
        >
            <Outlet />
        </CompanyAccessProvider>
    )
}