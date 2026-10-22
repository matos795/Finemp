import {
    Navigate,
    Route,
    Routes,
} from "react-router"
import { LoginPage } from "../../features/auth/pages/login-page"
import { ProtectedApp } from "../../features/auth/components/protected-app"
import { AppLayout } from "../../components/layout/app-layout"
import { DashboardPage } from "../../features/dashboard/pages/dashboard-page"
import { RequirePermission } from "../../features/auth/components/require-permission"
import { DrePage } from "../../features/dre/pages/dre-page"

export function AppRouter() {
    return (
        <Routes>
            <Route
                path="/login"
                element={<LoginPage />}
            />

            <Route element={<ProtectedApp />}>
                <Route element={<AppLayout />}>
                    <Route
                        index
                        element={<Navigate to="/dashboard" replace />}
                    />

                    <Route
                        path="/dashboard"
                        element={<DashboardPage />}
                    />

                    <Route
                        path="/dre"
                        element={
                            <RequirePermission permission="DRE_VIEW">
                                <DrePage />
                            </RequirePermission>
                        }
                    />
                </Route>
            </Route>

            <Route
                path="*"
                element={<Navigate to="/" replace />}
            />
        </Routes>
    )
}