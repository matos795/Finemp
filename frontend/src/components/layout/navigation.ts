import type { CompanyPermission } from "../../features/auth/types/auth.types"
import type { MenuIconName } from "./menu-icons"

export interface NavigationItem {
    id: string
    label: string
    /** Rota React. Sem rota = tela ainda não ligada à API (inativa, D5). */
    path?: string
    /** Qualquer uma das permissões libera o item (só UX; o backend autoriza). */
    permissions?: CompanyPermission[]
}

export interface NavigationModule {
    id: string
    label: string
    icon: MenuIconName
    items: NavigationItem[]
}

/**
 * Menu atual do React, exibido com o visual do protótipo homologado.
 * A organização completa do menu homologado é a fatia S2.
 */
export const navigationModules: NavigationModule[] = [
    {
        id: "painel",
        label: "Painel",
        icon: "fluxo",
        items: [
            {
                id: "painel",
                label: "Painel",
                path: "/dashboard",
                permissions: ["DASHBOARD_VIEW"],
            },
        ],
    },
    {
        id: "dre",
        label: "DRE",
        icon: "rel",
        items: [
            {
                id: "dre",
                label: "DRE",
                path: "/dre",
                permissions: ["DRE_VIEW"],
            },
        ],
    },
    {
        id: "balanco",
        label: "Balanço",
        icon: "ctb",
        items: [
            {
                id: "balanco",
                label: "Balanço",
                path: "/balance-sheet",
                permissions: ["BALANCE_SHEET_VIEW"],
            },
        ],
    },
    {
        id: "fluxo",
        label: "Fluxo de Caixa",
        icon: "fluxo",
        items: [
            {
                id: "fluxo",
                label: "Fluxo de Caixa",
                path: "/cash-flow",
                permissions: ["CASH_FLOW_VIEW"],
            },
        ],
    },
    {
        id: "indicadores",
        label: "Indicadores",
        icon: "orc",
        items: [
            {
                id: "indicadores",
                label: "Indicadores",
                path: "/indicators",
                permissions: ["INDICATORS_VIEW"],
            },
        ],
    },
    {
        id: "classificacao",
        label: "Classificação",
        icon: "cad",
        items: [
            {
                id: "classificacao",
                label: "Classificação",
                path: "/classification",
                permissions: ["ACCOUNT_CLASSIFICATION_MANAGE"],
            },
        ],
    },
    {
        id: "lancamentos",
        label: "Lançamentos",
        icon: "div",
        items: [
            {
                id: "lancamentos",
                label: "Lançamentos",
                path: "/entries",
                permissions: ["MANUAL_ENTRY_MANAGE"],
            },
        ],
    },
    {
        id: "cad",
        label: "Cadastros",
        icon: "cad",
        items: [
            {
                id: "cadContas",
                label: "Contas bancárias",
                path: "/bank-accounts",
                permissions: [
                    "BANK_ACCOUNT_VIEW",
                    "BANK_ACCOUNT_MANAGE",
                ],
            },
        ],
    },
]

/** Item visível: sem permissão exigida ou com qualquer uma delas. */
export function isItemVisible(
    item: NavigationItem,
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return (
        !item.permissions ||
        item.permissions.some(hasPermission)
    )
}

export function findNavigationByPath(
    modules: NavigationModule[],
    pathname: string,
) {
    for (const module of modules) {
        const item = module.items.find(
            (candidate) =>
                candidate.path !== undefined &&
                (pathname === candidate.path ||
                    pathname.startsWith(`${candidate.path}/`)),
        )

        if (item) {
            return { module, item }
        }
    }

    return null
}
