import {
    Navigate,
    Route,
    Routes,
} from "react-router"
import { LoginPage } from "../../features/auth/pages/login-page"
import { ActivatePage } from "../../features/auth/pages/activate-page"
import { ForgotPasswordPage } from "../../features/auth/pages/forgot-password-page"
import { ResetPasswordPage } from "../../features/auth/pages/reset-password-page"
import { IDENTITY_PATHS } from "../../features/auth/lib/identity-flow"
import { ProtectedApp } from "../../features/auth/components/protected-app"
import { AppLayout } from "../../components/layout/app-layout"
import { DashboardPage } from "../../features/dashboard/pages/dashboard-page"
import { CompanyDetailsPage } from "../../features/companies/pages/company-details-page"
import { RequirePermission } from "../../features/auth/components/require-permission"
import { RequirePermissionGroups } from "../../features/auth/components/require-permission-groups"
import { RequirePlatformPermission } from "../../features/auth/components/require-platform-permission"
import { DrePage } from "../../features/dre/pages/dre-page"
import { BusinessPartnersPage } from "../../features/business-partners/pages/business-partners-page"
import { BUSINESS_PARTNER_READ_PERMISSIONS } from "../../features/business-partners/lib/business-partner-access"
import { BankAccountsPage } from "../../features/bank-accounts/pages/bank-accounts-page"
import { FinancialCategoriesPage } from "../../features/financial-categories/pages/financial-categories-page"
import { FINANCIAL_CATEGORY_READ_PERMISSIONS } from "../../features/financial-categories/lib/financial-category-access"
import { StatementsPage } from "../../features/statements/pages/statements-page"
import { OpenTitlesPage } from "../../features/reports/pages/open-titles-page"
import { MonthlyCashFlowPage } from "../../features/reports/pages/monthly-cash-flow-page"
import { DailyCashFlowPage } from "../../features/reports/pages/daily-cash-flow-page"
import { CashFlowSchedulePage } from "../../features/reports/pages/cash-flow-schedule-page"
import { AccountBalancesPage } from "../../features/reports/pages/account-balances-page"
import { PresentationResultsPage } from "../../features/reports/pages/presentation-results-page"
import { ReceivedPaymentsByMethodPage } from "../../features/reports/pages/received-payments-by-method-page"
import { OperationalDrePage } from "../../features/operational-dre/pages/operational-dre-page"
import { PlatformAdminPage } from "../../features/platform-admin/pages/platform-admin-page"
import { PlatformUsersPage } from "../../features/platform-admin/pages/platform-users-page"
import { CompanyAuditTrailPage, PlatformActivityLogPage } from "../../features/audit/pages/audit-pages"
import { PlatformClientsPage } from "../../features/platform-admin/pages/platform-clients-page"
import { ResultCentersPage } from "../../features/result-centers/pages/result-centers-page"
import { FinancialPeriodsPage } from "../../features/financial-periods/pages/financial-periods-page"
import { FaqPage } from "../../features/help/pages/faq-page"
import { TutorialsPage } from "../../features/help/pages/tutorials-page"
import { AccountingAccountsPage } from "../../features/accounting/pages/accounting-accounts-page"
import { AccountingMovementsPage } from "../../features/accounting/pages/accounting-movements-page"
import { CompaniesPage } from "../../features/companies/pages/companies-page"
import { FinancialEntriesPage } from "../../features/financial-entries/pages/financial-entries-page"
import { FINANCIAL_ENTRY_READ_PERMISSIONS } from "../../features/financial-entries/lib/financial-entry-access"
import { PlanningMeetingsPage } from "../../features/planning/pages/planning-meetings-page"
import { PlanningGoalsPage } from "../../features/planning/pages/planning-goals-page"
import { PlanningActionsPage } from "../../features/planning/pages/planning-actions-page"
import { ContractsPage } from "../../features/contracts/pages/contracts-page"

export function AppRouter() {
    return (
        <Routes>
            {/* Públicas: funcionam sem sessão, fora de ProtectedApp. */}
            <Route
                path={IDENTITY_PATHS.login}
                element={<LoginPage />}
            />

            <Route
                path={IDENTITY_PATHS.activate}
                element={<ActivatePage />}
            />

            <Route
                path={IDENTITY_PATHS.forgotPassword}
                element={<ForgotPasswordPage />}
            />

            <Route
                path={IDENTITY_PATHS.resetPassword}
                element={<ResetPasswordPage />}
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
                        path="/company-details"
                        element={<CompanyDetailsPage />}
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
                        path="/admin/users"
                        element={
                            <RequirePlatformPermission permission="PLATFORM_USER_MANAGE">
                                <PlatformUsersPage />
                            </RequirePlatformPermission>
                        }
                    />

                    <Route
                        path="/admin/activity-log"
                        element={
                            <RequirePlatformPermission permission="SYSTEM_ADMIN">
                                <PlatformActivityLogPage />
                            </RequirePlatformPermission>
                        }
                    />

                    <Route
                        path="/audit"
                        element={
                            <RequirePlatformPermission permission="SYSTEM_ADMIN">
                                <CompanyAuditTrailPage />
                            </RequirePlatformPermission>
                        }
                    />

                    <Route
                        path="/admin/clients"
                        element={
                            <RequirePlatformPermission permission="ACCOUNTING_FIRM_VIEW">
                                <PlatformClientsPage />
                            </RequirePlatformPermission>
                        }
                    />

                    <Route
                        path="/result-centers"
                        element={
                            <RequirePermission
                                permission={[
                                    "RESULT_CENTER_VIEW",
                                    "RESULT_CENTER_MANAGE",
                                ]}
                            >
                                <ResultCentersPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/financial-periods"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_PERIOD_VIEW",
                                    "FINANCIAL_PERIOD_CLOSE",
                                    "FINANCIAL_PERIOD_REOPEN",
                                ]}
                            >
                                <FinancialPeriodsPage />
                            </RequirePermission>
                        }
                    />

                    <Route path="/help/faq" element={<FaqPage />} />
                    <Route path="/help/tutorials" element={<TutorialsPage />} />

                    <Route
                        path="/accounting-accounts"
                        element={
                            <RequirePermission
                                permission={[
                                    "ACCOUNT_CLASSIFICATION_VIEW",
                                    "ACCOUNT_CLASSIFICATION_MANAGE",
                                ]}
                            >
                                <AccountingAccountsPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/accounting-movements"
                        element={
                            <RequirePermission permission="ACCOUNTING_MOVEMENT_VIEW">
                                <AccountingMovementsPage />
                            </RequirePermission>
                        }
                    />

                    <Route path="/companies" element={<CompaniesPage />} />

                    <Route
                        path="/planning/meetings"
                        element={
                            <RequirePermission
                                permission={["PLANNING_VIEW", "PLANNING_MANAGE"]}
                            >
                                <PlanningMeetingsPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/planning/goals"
                        element={
                            <RequirePermission
                                permission={["PLANNING_VIEW", "PLANNING_MANAGE"]}
                            >
                                <PlanningGoalsPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/planning/actions"
                        element={
                            <RequirePermission
                                permission={["PLANNING_VIEW", "PLANNING_MANAGE"]}
                            >
                                <PlanningActionsPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/contracts"
                        element={
                            <RequirePermission
                                permission={["CONTRACT_VIEW", "CONTRACT_MANAGE"]}
                            >
                                <ContractsPage />
                            </RequirePermission>
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
                        path="/business-partners"
                        element={
                            <RequirePermission
                                permission={BUSINESS_PARTNER_READ_PERMISSIONS}
                            >
                                <BusinessPartnersPage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/financial-categories"
                        element={
                            <RequirePermission
                                permission={FINANCIAL_CATEGORY_READ_PERMISSIONS}
                            >
                                <FinancialCategoriesPage />
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
                        path="/clients/delinquency"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <OpenTitlesPage direction="INCOME" />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/clients/receipts-by-method"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <ReceivedPaymentsByMethodPage />
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
                        path="/reports/presentation"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <PresentationResultsPage />
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
                        path="/cash-flow/schedule"
                        element={
                            <RequirePermission
                                permission={[
                                    "FINANCIAL_ENTRY_VIEW",
                                    "FINANCIAL_ENTRY_MANAGE",
                                ]}
                            >
                                <CashFlowSchedulePage />
                            </RequirePermission>
                        }
                    />

                    <Route
                        path="/cash-flow/accounts"
                        element={
                            <RequirePermissionGroups
                                groups={[
                                    ["BANK_ACCOUNT_VIEW", "BANK_ACCOUNT_MANAGE"],
                                    ["FINANCIAL_ENTRY_VIEW", "FINANCIAL_ENTRY_MANAGE"],
                                ]}
                            >
                                <AccountBalancesPage />
                            </RequirePermissionGroups>
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
