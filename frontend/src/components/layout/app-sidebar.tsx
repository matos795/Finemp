import { useState } from "react"
import {
    NavLink,
    useLocation,
} from "react-router"
import { cn } from "cn"
import type { PlatformPermission } from "../../features/auth/types/auth.types"
import { useCompanyAccess } from "../../features/companies/company-access-context"
import { MenuIcon } from "./menu-icon"
import {
    findNavigationByPath,
    navigationModules,
    visibleNavigation,
} from "./navigation"

interface AppSidebarProps {
    /** Remove o menu fechado da navegação por teclado no celular. */
    inert?: boolean
    /** Chamado ao escolher uma tela (fecha o menu no celular). */
    onNavigate: () => void
}

/**
 * Menu lateral do protótipo homologado (#side): módulos em acordeão,
 * um aberto por vez; o módulo atual mostra a tela aberta quando fechado.
 */
export function AppSidebar({
    inert = false,
    onNavigate,
}: AppSidebarProps) {
    const { user, hasPermission } = useCompanyAccess()

    function hasPlatformPermission(
        permission: PlatformPermission,
    ) {
        return (user.platformPermissions ?? []).includes(permission)
    }
    const location = useLocation()

    const [openModuleId, setOpenModuleId] =
        useState<string | null>(null)

    const current = findNavigationByPath(
        navigationModules,
        location.pathname,
    )

    // Só telas publicadas e liberadas; o roadmap sem rota fica fora do menu.
    const modules = visibleNavigation(
        navigationModules,
        hasPermission,
        hasPlatformPermission,
    )

    function handleNavigate() {
        setOpenModuleId(null)
        onNavigate()
    }

    return (
        <aside
            id="side"
            aria-label="Menu principal"
            aria-hidden={inert || undefined}
            inert={inert || undefined}
        >
            {modules.map((module) => {
                const isCurrent =
                    current?.module.id === module.id

                if (module.standalone) {
                    const [item] = module.items

                    return (
                        <div
                            key={module.id}
                            className="mod"
                        >
                            <NavLink
                                to={item.path}
                                onClick={handleNavigate}
                                className={({ isActive }) =>
                                    cn("mod-h unico", isActive && "on")
                                }
                            >
                                <MenuIcon name={module.icon} />
                                {module.label}
                            </NavLink>
                        </div>
                    )
                }

                const isOpen = openModuleId === module.id

                return (
                    <div
                        key={module.id}
                        className="mod"
                    >
                        <button
                            type="button"
                            className={cn("mod-h", isCurrent && "atual")}
                            aria-expanded={isOpen}
                            onClick={() =>
                                setOpenModuleId(isOpen ? null : module.id)
                            }
                        >
                            <MenuIcon name={module.icon} />

                            <span className="mn">
                                {module.label}

                                {!isOpen && isCurrent && (
                                    <small>{current.item.label}</small>
                                )}
                            </span>

                            <span className="seta">
                                {isOpen ? "▾" : "▸"}
                            </span>
                        </button>

                        {isOpen &&
                            module.items.map((item) => (
                                <NavLink
                                    key={item.id}
                                    to={item.path}
                                    onClick={handleNavigate}
                                    className={({ isActive }) =>
                                        cn("mi", isActive && "on")
                                    }
                                >
                                    {item.label}
                                </NavLink>
                            ))}
                    </div>
                )
            })}
        </aside>
    )
}
