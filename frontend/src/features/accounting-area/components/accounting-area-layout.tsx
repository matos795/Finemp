import { useEffect, useLayoutEffect, useRef, useState } from "react"
import { Navigate, useLocation, useNavigate } from "react-router"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { useCompanySyncStatus } from "../hooks/use-accounting-area-queries"
import { defaultCompetence, syncedUntil } from "../lib/accounting-period"
import { baseStatusText } from "../lib/base-status"
import { ACCOUNTING_AREA_HOME, findAccountingScreen, findAccountingScreenById } from "../lib/accounting-screens"
import { AccountingScreenPage } from "../pages/accounting-screen-page"
import { CfpreScreen } from "../pages/cfpre-screen"
import { DeparaScreen } from "../pages/depara-screen"
import { AccountingToasts } from "./accounting-toasts"
import { AccountingInfoProvider } from "./accounting-info"
import { AccountingContextModal } from "./accounting-context-modal"
import { AccountingHeader } from "./accounting-header"
import { AccountingMenu } from "./accounting-menu"

const MOBILE_QUERY = "(max-width:900px)"
const SIDE_PREFERENCE_KEY = "finemp.ctb.menu.v1"

function readSidePreference() {
    try {
        return localStorage.getItem(SIDE_PREFERENCE_KEY) !== "0"
    } catch {
        return true
    }
}

function saveSidePreference(open: boolean) {
    try {
        localStorage.setItem(SIDE_PREFERENCE_KEY, open ? "1" : "0")
    } catch {
        // Preferência visual opcional.
    }
}

function isMobile() {
    return window.matchMedia(MOBILE_QUERY).matches
}

/** sideLigar/sideAplicar do protótipo: no celular o menu começa fechado e vira gaveta. */
function useSideMenu() {
    const [open, setOpen] = useState(() => (isMobile() ? false : readSidePreference()))
    const [mobile, setMobile] = useState(isMobile)

    useEffect(() => {
        const query = window.matchMedia(MOBILE_QUERY)
        const update = () => {
            setMobile(query.matches)
            setOpen(query.matches ? false : readSidePreference())
        }

        query.addEventListener("change", update)

        return () => query.removeEventListener("change", update)
    }, [])

    function toggle() {
        setOpen((previous) => {
            if (!isMobile()) {
                saveSidePreference(!previous)
            }

            return !previous
        })
    }

    return { open, mobile, toggle, close: () => setOpen(false) }
}

/** hdrMedir do protótipo: --hdrH acompanha a altura do cabeçalho fixo. */
function useHeaderHeight() {
    const ref = useRef<HTMLDivElement>(null)

    useLayoutEffect(() => {
        const element = ref.current?.querySelector<HTMLElement>("#hdrFixo")

        if (!element) {
            return
        }

        const measure = () => document.documentElement.style.setProperty("--hdrH", `${element.offsetHeight}px`)
        measure()
        const observer = new ResizeObserver(measure)
        observer.observe(element)

        return () => observer.disconnect()
    }, [])

    return ref
}

/**
 * Shell da Área da Contabilidade no padrão do protótipo (div.app#app):
 * botão do menu, gaveta, menu agrupado, coluna principal com cabeçalho fixo
 * e a section da aba atual.
 */
export function AccountingAreaLayout() {
    const location = useLocation()
    const navigate = useNavigate()
    const side = useSideMenu()
    const [contextOpen, setContextOpen] = useState(false)
    const rootRef = useHeaderHeight()
    const { company, competence, setCompetence } = useAccountingContext()
    const syncStatus = useCompanySyncStatus(company?.companyId ?? null)
    const current = findAccountingScreen(location.pathname)

    if (!current) {
        return <Navigate to={ACCOUNTING_AREA_HOME} replace />
    }

    const baseUntil = syncStatus.isSuccess ? syncedUntil(syncStatus.data) : null
    const effectiveCompetence = competence ?? defaultCompetence(baseUntil, new Date())
    const baseText = baseStatusText({
        hasCompany: company !== null,
        loading: syncStatus.isLoading,
        errorStatus: syncStatus.isError ? apiErrorStatus(syncStatus.error) ?? 0 : null,
        status: syncStatus.data,
    })

    function openTab(tab: string) {
        const target = findAccountingScreenById(tab)

        // sideLigar: escolher uma aba no menu fecha o menu.
        side.close()

        if (target) {
            navigate(target.path)
            window.scrollTo({ top: 0 })
        }
    }

    return (
        <div className="ctbp" ref={rootRef}>
            <AccountingToasts>
            <AccountingInfoProvider>
                    <div className={side.open ? "app" : "app closed"} id="app">
                        <button
                            className="side-tog"
                            id="sideTog"
                            type="button"
                            aria-label={side.open ? "Recolher menu" : "Abrir menu"}
                            aria-expanded={side.open}
                            aria-controls="side"
                            title="Recolher ou abrir o menu"
                            onClick={side.toggle}
                        >
                            <span className="burger" aria-hidden="true">
                                <i />
                                <i />
                                <i />
                            </span>
                        </button>
                        <div className="side-bg" id="sideBg" hidden={!(side.open && side.mobile)} onClick={side.close} />

                        <AccountingMenu currentTab={current.screen.id} onOpenTab={openTab} />

                        <div className="main-col">
                            <div className="wrap">
                                <AccountingHeader
                                    current={current}
                                    competence={effectiveCompetence}
                                    baseUntil={baseUntil}
                                    onOpenTab={openTab}
                                    onOpenContext={() => setContextOpen(true)}
                                />

                                <main>
                                    {current.screen.id === "depara" ? (
                                        <DeparaScreen key={company?.companyId ?? "none"} />
                                    ) : current.screen.id === "cfpre" ? (
                                        <CfpreScreen
                                            screen={current.screen}
                                            competence={effectiveCompetence}
                                            baseText={baseText}
                                            onCompetenceChange={setCompetence}
                                        />
                                    ) : (
                                        <AccountingScreenPage
                                            key={current.screen.id}
                                            screen={current.screen}
                                            competence={effectiveCompetence}
                                            baseText={baseText}
                                            onCompetenceChange={setCompetence}
                                        />
                                    )}
                                </main>
                            </div>
                        </div>
                    </div>

                    <AccountingContextModal open={contextOpen} onClose={() => setContextOpen(false)} />
            </AccountingInfoProvider>
            </AccountingToasts>
        </div>
    )
}
