import { hasPlatformAccess } from "../../features/auth/types/auth.types"
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
        return hasPlatformAccess(user, permission)
    }
    const location = useLocation()

    const [openModuleId, setOpenModuleId] =
        useState<string | null>(null)

    const current = findNavigationByPath(
        navigationModules,
        location.pathname,
    )

    const showRoadmap =
        (user.platformPermissions ?? []).includes("SYSTEM_ADMIN")

    // Clientes continuam vendo só telas publicadas. SYSTEM_ADMIN também vê
    // o roadmap homologado, mas itens sem rota permanecem indisponíveis.
    const modules = visibleNavigation(
        navigationModules,
        hasPermission,
        hasPlatformPermission,
        showRoadmap,
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
                            {item.available ? (
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
                            ) : (
                                <div
                                    className="mod-h unico roadmap-soon"
                                    aria-disabled="true"
                                >
                                    <MenuIcon name={module.icon} />
                                    <span className="mn">{module.label}</span>
                                    <small className="roadmap-soon-badge">Em breve</small>
                                </div>
                            )}
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
                            module.items.map((item) =>
                                item.available ? (
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
                                ) : (
                                    <div
                                        key={item.id}
                                        className="mi roadmap-soon"
                                        aria-disabled="true"
                                    >
                                        <span>{item.label}</span>
                                        <small className="roadmap-soon-badge">Em breve</small>
                                    </div>
                                ),
                            )}
                    </div>
                )
            })}
        </aside>
    )
}
