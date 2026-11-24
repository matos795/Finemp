import { useState } from "react"
import {
    ACCOUNTING_AREA_SECTION_LABEL,
    accountingModules,
    type AccountingModule,
} from "../lib/accounting-screens"
import { menuMatches, menuNorm } from "../lib/menu-search"

/** Ícones do protótipo (constante ICONES, viewBox 0 0 24 24). */
const ICONS: Record<AccountingModule["icon"], string> = {
    fech: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1"/><path d="M8.5 13l2.5 2.5 4.5-5"/>',
    ata: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/>',
}

function Chevron() {
    return (
        <svg className="chev" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M9 6l6 6-6 6" />
        </svg>
    )
}

interface MenuState {
    tab: string | null
    groups: Set<string>
    subgroups: Set<string>
}

/** menuMarcar: abre só o grupo e o subgrupo da aba atual. */
function markedFor(tab: string | null): MenuState {
    const groups = new Set<string>()
    const subgroups = new Set<string>()

    for (const module of accountingModules) {
        for (const subgroup of module.subgroups) {
            if (subgroup.screens.some((screen) => screen.id === tab)) {
                groups.add(module.id)
                subgroups.add(`${module.id}/${subgroup.label}`)
            }
        }
    }

    return { tab, groups, subgroups }
}

interface AccountingMenuProps {
    currentTab: string | null
    onOpenTab: (tab: string) => void
}

/**
 * Menu lateral do protótipo (menuMontar/menuMarcar/menuFiltrar): separador
 * "Contabilidade (equipe Finemp)", grupos com ícone, subgrupos e abas em pílula.
 */
export function AccountingMenu({ currentTab, onOpenTab }: AccountingMenuProps) {
    const [state, setState] = useState<MenuState>(() => markedFor(currentTab))
    const [query, setQuery] = useState("")

    if (state.tab !== currentTab) {
        setState(markedFor(currentTab))
    }

    const q = menuNorm(query).trim()
    const searching = q.length > 0

    function toggle(kind: "groups" | "subgroups", key: string) {
        setState((previous) => {
            const next = new Set(previous[kind])

            if (next.has(key)) {
                next.delete(key)
            } else {
                next.add(key)
            }

            return { ...previous, [kind]: next }
        })
    }

    return (
        <aside className="side" id="side" aria-label="Menu">
            <div className="side-tit">Menu</div>
            <input
                className="menu-busca"
                id="menuBusca"
                type="search"
                placeholder="Buscar aba…"
                aria-label="Buscar aba no menu"
                autoComplete="off"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
            />

            <nav className={searching ? "tabs busca" : "tabs"} role="tablist" id="tabs" aria-label="Menu de abas">
                <div className="nav-sep" hidden={searching}>
                    {ACCOUNTING_AREA_SECTION_LABEL}
                </div>

                {accountingModules.map((module) => {
                    const matches = (label: string, subgroupLabel: string) =>
                        menuMatches(query, label, module.label, subgroupLabel)
                    const hasMatch = module.subgroups.some((subgroup) =>
                        subgroup.screens.some((screen) => matches(screen.label, subgroup.label)),
                    )
                    const isCurrent = module.subgroups.some((subgroup) =>
                        subgroup.screens.some((screen) => screen.id === currentTab),
                    )
                    const isOpen = searching ? hasMatch : state.groups.has(module.id)
                    const classes = ["nav-g", isOpen && "open", !searching && isCurrent && "atual"]
                        .filter(Boolean)
                        .join(" ")

                    return (
                        <div key={module.id} className={classes} data-g={module.label} hidden={!hasMatch}>
                            <button
                                className="nav-gh"
                                type="button"
                                data-grp={module.label}
                                aria-expanded={isOpen}
                                onClick={() => toggle("groups", module.id)}
                            >
                                <svg
                                    className="ic"
                                    viewBox="0 0 24 24"
                                    aria-hidden="true"
                                    dangerouslySetInnerHTML={{ __html: ICONS[module.icon] }}
                                />
                                <span>{module.label}</span>
                                <Chevron />
                            </button>

                            <div className="nav-items">
                                {module.subgroups.map((subgroup) => {
                                    const pills = subgroup.screens.map((screen) => (
                                        <button
                                            key={screen.id}
                                            className="pill"
                                            role="tab"
                                            type="button"
                                            data-tab={screen.id}
                                            id={`t-${screen.id}`}
                                            aria-selected={screen.id === currentTab}
                                            hidden={!matches(screen.label, subgroup.label)}
                                            onClick={() => onOpenTab(screen.id)}
                                        >
                                            {screen.label}
                                        </button>
                                    ))

                                    if (!subgroup.label) {
                                        return pills
                                    }

                                    const key = `${module.id}/${subgroup.label}`
                                    const subgroupHasMatch = subgroup.screens.some((screen) =>
                                        matches(screen.label, subgroup.label),
                                    )
                                    const subgroupOpen = searching ? subgroupHasMatch : state.subgroups.has(key)

                                    return (
                                        <div
                                            key={key}
                                            className={subgroupOpen ? "nav-sg open" : "nav-sg"}
                                            hidden={!subgroupHasMatch}
                                        >
                                            <button
                                                className="nav-sgh"
                                                type="button"
                                                data-sgrp="1"
                                                aria-expanded={subgroupOpen}
                                                onClick={() => toggle("subgroups", key)}
                                            >
                                                <span>{subgroup.label}</span>
                                                <Chevron />
                                            </button>
                                            <div className="nav-sitems">{pills}</div>
                                        </div>
                                    )
                                })}
                            </div>
                        </div>
                    )
                })}
            </nav>
        </aside>
    )
}
