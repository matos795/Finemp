import { useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import logoUrl from "../../assets/finemp-logo.png"
import { useCompanyAccess } from "../../features/companies/company-access-context"
import { logout } from "../../features/auth/api/auth-api"
import { currentUserQueryKey } from "../../features/auth/hooks/use-current-user"
import { CompanySwitcher } from "./company-switcher"

const todayFormatter =
    new Intl.DateTimeFormat("pt-BR")

interface AppHeaderProps {
    menuOpen: boolean
    onToggleMenu: () => void
}

/**
 * Cabeçalho do protótipo homologado (header.top).
 * "Saldo atual" e a contagem de lançamentos do protótipo dependem de
 * dados que a API ainda não fornece e não são calculados aqui.
 */
export function AppHeader({
    menuOpen,
    onToggleMenu,
}: AppHeaderProps) {
    const {
        user,
        selectedCompany,
    } = useCompanyAccess()

    const queryClient = useQueryClient()
    const navigate = useNavigate()

    async function handleLogout() {
        await logout()

        queryClient.setQueryData(
            currentUserQueryKey,
            null,
        )

        navigate("/login", {
            replace: true,
        })
    }

    return (
        <header className="top">
            <button
                type="button"
                className={menuOpen ? "icobtn on" : "icobtn"}
                id="btnMenu"
                aria-label="Mostrar ou ocultar o menu"
                title="Mostrar / ocultar menu"
                onClick={onToggleMenu}
            >
                <svg
                    width="20"
                    height="20"
                    viewBox="0 0 20 20"
                    aria-hidden="true"
                >
                    <path
                        d="M3 5h14M3 10h14M3 15h14"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                    />
                </svg>
            </button>

            <img
                id="logo"
                alt="FINEMP Finanças Empresariais"
                src={logoUrl}
            />

            <div className="hd-main">
                <h1>
                    Fluxo de Caixa{" "}
                    {selectedCompany && (
                        <span style={{ color: "var(--gold)" }}>
                            · {selectedCompany.name}
                        </span>
                    )}
                </h1>

                <div className="hd-meta">
                    <span>
                        Hoje: <b>{todayFormatter.format(new Date())}</b>
                    </span>
                </div>
            </div>

            <div className="hd-right">
                {user.platformPermissions?.includes("SYSTEM_ADMIN") && <small>Administrador do sistema</small>}
                <CompanySwitcher />

                <button
                    type="button"
                    className="btn sec"
                    title={`${user.name} · ${user.email}`}
                    onClick={handleLogout}
                >
                    Sair
                </button>
            </div>
        </header>
    )
}
