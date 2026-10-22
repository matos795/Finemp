import {
    BarChart3,
    ChartNoAxesCombined,
    Landmark,
    LayoutDashboard,
    LineChart,
    Settings2,
    WalletCards,
} from "lucide-react"

import {
    NavLink,
} from "react-router"
import type { CompanyPermission } from "../../features/auth/types/auth.types"
import { useCompanyAccess } from "../../features/companies/company-access-context"
import { cn } from "cn"


interface NavigationItem {
    label: string
    path: string
    permission: CompanyPermission
    icon: React.ComponentType<{
        className?: string
    }>
}

const navigation: NavigationItem[] = [
    {
        label: "Painel",
        path: "/dashboard",
        permission: "DASHBOARD_VIEW",
        icon: LayoutDashboard,
    },
    {
        label: "DRE",
        path: "/dre",
        permission: "DRE_VIEW",
        icon: BarChart3,
    },
    {
        label: "Balanço",
        path: "/balance-sheet",
        permission: "BALANCE_SHEET_VIEW",
        icon: Landmark,
    },
    {
        label: "Fluxo de Caixa",
        path: "/cash-flow",
        permission: "CASH_FLOW_VIEW",
        icon: LineChart,
    },
    {
        label: "Indicadores",
        path: "/indicators",
        permission: "INDICATORS_VIEW",
        icon: ChartNoAxesCombined,
    },
    {
        label: "Classificação",
        path: "/classification",
        permission: "ACCOUNT_CLASSIFICATION_MANAGE",
        icon: Settings2,
    },
    {
        label: "Lançamentos",
        path: "/entries",
        permission: "MANUAL_ENTRY_MANAGE",
        icon: WalletCards,
    },
]

export function AppSidebar() {
    const {
        selectedCompany,
        hasPermission,
    } = useCompanyAccess()

    const visibleNavigation =
        navigation.filter((item) =>
            hasPermission(item.permission),
        )

    return (
        <aside className="hidden h-screen w-[260px] shrink-0 flex-col bg-[#102238] text-white lg:flex">
            <div className="flex h-20 items-center px-6">
                <div>
                    <p className="text-lg font-semibold tracking-wide">
                        FINEMP
                    </p>

                    <p className="text-xs text-white/40">
                        Inteligência contábil
                    </p>
                </div>
            </div>

            <div className="px-4 pb-3">
                <div className="rounded-xl bg-white/[0.06] px-4 py-3">
                    <p className="text-xs uppercase tracking-wider text-white/40">
                        Perfil
                    </p>

                    <p className="mt-1 text-sm font-medium">
                        {selectedCompany?.role ?? "Sem acesso"}
                    </p>
                </div>
            </div>

            <nav className="flex-1 space-y-1 px-3 py-4">
                {visibleNavigation.map((item) => {
                    const Icon = item.icon

                    return (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            className={({ isActive }) =>
                                cn(
                                    "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors",
                                    isActive
                                        ? "bg-[#c7a35a] font-medium text-[#102238]"
                                        : "text-white/65 hover:bg-white/[0.06] hover:text-white",
                                )
                            }
                        >
                            <Icon className="size-4" />
                            {item.label}
                        </NavLink>
                    )
                })}
            </nav>

            <div className="border-t border-white/10 p-5 text-xs text-white/30">
                FINEMP
            </div>
        </aside>
    )
}