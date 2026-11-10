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
import { RequirePlatformPermission } from "../../features/auth/components/require-platform-permission"
import { DrePage } from "../../features/dre/pages/dre-page"
import { BankAccountsPage } from "../../features/bank-accounts/pages/bank-accounts-page"
import { StatementsPage } from "../../features/statements/pages/statements-page"
import { OpenTitlesPage } from "../../features/reports/pages/open-titles-page"
import { MonthlyCashFlowPage } from "../../features/reports/pages/monthly-cash-flow-page"
import { DailyCashFlowPage } from "../../features/reports/pages/daily-cash-flow-page"
import { OperationalDrePage } from "../../features/operational-dre/pages/operational-dre-page"
import { PlatformAdminPage } from "../../features/platform-admin/pages/platform-admin-page"
import { FinancialEntriesPage } from "../../features/financial-entries/pages/financial-entries-page"
import { FINANCIAL_ENTRY_READ_PERMISSIONS } from "../../features/financial-entries/lib/financial-entry-access"

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
                        path="/admin"
                        element={
                            <RequirePlatformPermission
                                permission={[
                                    "ACCOUNTING_FIRM_VIEW",
                                    "ACCOUNTING_FIRM_CREATE",
                                    "PLATFORM_USER_MANAGE",
                                ]}
                            >
                                <PlatformAdminPage />
                            </RequirePlatformPermission>
                        }
                    />

                    <Route
                        path="/dre"
                        element={
                            <RequirePermission permission="DRE_VIEW">
                                <DrePage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/bank-accounts"
                        element={
                            <RequirePermission
                                permission={[
                                    "BANK_ACCOUNT_VIEW",
                                    "BANK_ACCOUNT_MANAGE",
                                ]}
                            >
                                <BankAccountsPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/financial-entries"
                        element={
                            <RequirePermission
                                permission={FINANCIAL_ENTRY_READ_PERMISSIONS}
                            >
                                <FinancialEntriesPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/statements"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <StatementsPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/reports/dre"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <OperationalDrePage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/reports/receivables"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <OpenTitlesPage
                                    key="INCOME"
                                    direction="INCOME"
                                />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/reports/payables"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <OpenTitlesPage
                                    key="EXPENSE"
                                    direction="EXPENSE"
                                />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/cash-flow/monthly"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <MonthlyCashFlowPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/cash-flow/daily"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <DailyCashFlowPage />
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
