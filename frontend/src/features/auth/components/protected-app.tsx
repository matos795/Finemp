import { Navigate, Outlet } from "react-router"
import { useCurrentUser } from "../hooks/use-current-user"
import { CompanyAccessProvider } from "../../companies/company-access-provider"

export function ProtectedApp() {
    const currentUser = useCurrentUser()

    if (currentUser.isLoading) {
        return (
            <div className="fin min-h-screen">
                <div className="empty">Carregando…</div>
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