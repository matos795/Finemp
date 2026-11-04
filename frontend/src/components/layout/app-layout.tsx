import {
    useEffect,
    useState,
} from "react"
import {
    Outlet,
    useLocation,
} from "react-router"
import { cn } from "cn"
import { AppSidebar } from "./app-sidebar"
import { AppHeader } from "./app-header"
import { PageActions } from "./page-actions"
import { BarActionsContext } from "./bar-actions-context"
import {
    findNavigationByPath,
    navigationModules,
} from "./navigation"

const MOBILE_QUERY = "(max-width:1000px)"
const SIDE_PREFERENCE_KEY = "finemp.ui.side"

function readSidePreference() {
    try {
        return localStorage.getItem(SIDE_PREFERENCE_KEY) !== "off"
    } catch {
        return true
    }
}

function saveSidePreference(open: boolean) {
    try {
        localStorage.setItem(SIDE_PREFERENCE_KEY, open ? "on" : "off")
    } catch {
        // Preferência visual opcional.
    }
}

function useIsMobile() {
    const [isMobile, setIsMobile] = useState(
        () => window.matchMedia(MOBILE_QUERY).matches,
    )

    useEffect(() => {
        const query = window.matchMedia(MOBILE_QUERY)
        const update = () => setIsMobile(query.matches)

        query.addEventListener("change", update)

        return () => query.removeEventListener("change", update)
    }, [])

    return isMobile
}

/** Trilha do protótipo (#trilha): módulo › tela. */
function Breadcrumb() {
    const location = useLocation()

    const current = findNavigationByPath(
        navigationModules,
        location.pathname,
    )

    if (!current) {
        return <div id="trilha" />
    }

    const { module, item } = current

    const showModule =
        module.items.length > 1 ||
        module.label.toLowerCase() !== item.label.toLowerCase()

    return (
        <div
            id="trilha"
            title={`${module.label} › ${item.label}`}
        >
            {showModule && (
                <>
                    <span className="grp">{module.label}</span>
                    <span className="sep">›</span>
                </>
            )}

            <b>{item.label}</b>
        </div>
    )
}

/**
 * Shell do protótipo homologado: header.top, .corpo com #side e
 * .conteudo com a barra (trilha + ações). Todo o tema fica sob .fin.
 */
export function AppLayout() {
    const isMobile = useIsMobile()

    const [sideOpen, setSideOpen] =
        useState(readSidePreference)

    const [sideMobileOpen, setSideMobileOpen] =
        useState(false)

    function toggleMenu() {
        if (isMobile) {
            setSideMobileOpen((open) => !open)
            return
        }

        setSideOpen((open) => {
            saveSidePreference(!open)
            return !open
        })
    }

    const [barActionsSlot, setBarActionsSlot] =
        useState<HTMLElement | null>(null)

    const mobileOpen = isMobile && sideMobileOpen

    return (
        <div
            className={cn(
                "fin min-h-screen",
                !sideOpen && !isMobile && "side-off",
                mobileOpen && "side-mob",
            )}
        >
            <AppHeader
                menuOpen={isMobile ? sideMobileOpen : sideOpen}
                onToggleMenu={toggleMenu}
            />

            <div className="corpo">
                <AppSidebar
                    inert={isMobile && !mobileOpen}
                    onNavigate={() => setSideMobileOpen(false)}
                />

                <div className="conteudo">
                    <div className="barra">
                        <Breadcrumb />
                        <PageActions onSlotChange={setBarActionsSlot} />
                    </div>

                    <main id="main">
                        <BarActionsContext.Provider value={barActionsSlot}>
                            <Outlet />
                        </BarActionsContext.Provider>
                    </main>
                </div>
            </div>

            {mobileOpen && (
                <div
                    id="backdrop"
                    onClick={() => setSideMobileOpen(false)}
                />
            )}
        </div>
    )
}
