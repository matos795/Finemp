import {
    BarChart3,
    Banknote,
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
    /** Qualquer uma das permissões libera o item (só UX). */
    permissions: CompanyPermission[]
    icon: React.ComponentType<{
        className?: string
    }>
}

interface NavigationSection {
    /** Nome do grupo no menu do protótipo homologado; vazio = sem título. */
    title: string | null
    items: NavigationItem[]
}

const mainNavigation: NavigationItem[] = [
    {
        label: "Painel",
        path: "/dashboard",
        permissions: ["DASHBOARD_VIEW"],
        icon: LayoutDashboard,
    },
    {
        label: "DRE",
        path: "/dre",
        permissions: ["DRE_VIEW"],
        icon: BarChart3,
    },
    {
        label: "Balanço",
        path: "/balance-sheet",
        permissions: ["BALANCE_SHEET_VIEW"],
        icon: Landmark,
    },
    {
        label: "Fluxo de Caixa",
        path: "/cash-flow",
        permissions: ["CASH_FLOW_VIEW"],
        icon: LineChart,
    },
    {
        label: "Indicadores",
        path: "/indicators",
        permissions: ["INDICATORS_VIEW"],
        icon: ChartNoAxesCombined,
    },
    {
        label: "Classificação",
        path: "/classification",
        permissions: ["ACCOUNT_CLASSIFICATION_MANAGE"],
        icon: Settings2,
    },
    {
        label: "Lançamentos",
        path: "/entries",
        permissions: ["MANUAL_ENTRY_MANAGE"],
        icon: WalletCards,
    },
]

const navigation: NavigationSection[] = [
    {
        title: null,
        items: mainNavigation,
    },
    {
        title: "Cadastros",
        items: [
            {
                label: "Contas bancárias",
                path: "/bank-accounts",
                permissions: [
                    "BANK_ACCOUNT_VIEW",
                    "BANK_ACCOUNT_MANAGE",
                ],
                icon: Banknote,
            },
        ],
    },
]

export function AppSidebar() {
    const {
        selectedCompany,
        hasPermission,
    } = useCompanyAccess()

    const visibleNavigation = navigation
        .map((section) => ({
            ...section,
            items: section.items.filter((item) =>
                item.permissions.some(hasPermission),
            ),
        }))
        .filter((section) => section.items.length > 0)

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

            <nav className="flex-1 space-y-4 px-3 py-4">
                {visibleNavigation.map((section) => (
                    <div
                        key={section.title ?? "main"}
                        className="space-y-1"
                    >
                        {section.title && (
                            <p className="px-3 pb-1 text-xs uppercase tracking-wider text-white/40">
                                {section.title}
                            </p>
                        )}

                        {section.items.map((item) => {
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
                    </div>
                ))}
            </nav>

            <div className="border-t border-white/10 p-5 text-xs text-white/30">
                FINEMP
            </div>
        </aside>
    )
}