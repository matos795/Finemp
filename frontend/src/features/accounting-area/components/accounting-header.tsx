import { useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import logoUrl from "../../../assets/finemp-logo.png"
import { logout } from "../../auth/api/auth-api"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user"
import { useAccountingContext } from "../accounting-context"
import { monthLabel } from "../lib/accounting-period"
import { ACCOUNTING_AREA_QUERY_ROOT } from "../lib/query-keys"
import type { AccountingModule, AccountingScreen, AccountingSubgroup } from "../lib/accounting-screens"

const LONG_MONTHS = [
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
]

function longMonth(competence: string) {
    const [year, month] = competence.split("-")

    return `${LONG_MONTHS[Number(month) - 1]} de ${year}`
}

interface AccountingHeaderProps {
    current: { module: AccountingModule; subgroup: AccountingSubgroup; screen: AccountingScreen } | null
    competence: string
    baseUntil: string | null
    onOpenTab: (tab: string) => void
    onOpenContext: () => void
}

/** Submenu no cabeçalho (hdrNav do protótipo): subgrupos irmãos e abas do subgrupo atual. */
function HeaderNav({ current, onOpenTab }: Pick<AccountingHeaderProps, "current" | "onOpenTab">) {
    if (!current) {
        return <nav className="hdr-nav no-print" id="hdrNav" aria-label="Abas desta área" hidden />
    }

    const { module, subgroup, screen } = current
    const pill = (item: AccountingScreen) => (
        <button
            key={item.id}
            type="button"
            className={item.id === screen.id ? "hn-p on" : "hn-p"}
            data-tab-go={item.id}
            aria-current={item.id === screen.id ? "page" : undefined}
            onClick={() => onOpenTab(item.id)}
        >
            {item.label}
        </button>
    )

    return (
        <nav className="hdr-nav no-print" id="hdrNav" aria-label="Abas desta área">
            {subgroup.label && (
                <div className="hn-l hn-sub">
                    {module.subgroups.map((sibling) => (
                        <button
                            key={sibling.label}
                            type="button"
                            className={sibling === subgroup ? "hn-s on" : "hn-s"}
                            data-tab-go={sibling.screens[0].id}
                            onClick={() => onOpenTab(sibling.screens[0].id)}
                        >
                            {sibling.label}
                        </button>
                    ))}
                </div>
            )}
            <div className="hn-l">{subgroup.screens.map(pill)}</div>
        </nav>
    )
}

/**
 * Cabeçalho fixo do protótipo (#hdrFixo): marca, empresa, filtro de contexto,
 * linha do título com submenu e botões, e a legenda.
 */
export function AccountingHeader({ current, competence, baseUntil, onOpenTab, onOpenContext }: AccountingHeaderProps) {
    const { firm, client, company, companyGrant } = useAccountingContext()
    const queryClient = useQueryClient()
    const navigate = useNavigate()

    async function handleLogout() {
        await logout()
        queryClient.setQueryData(currentUserQueryKey, null)
        queryClient.removeQueries({ queryKey: [ACCOUNTING_AREA_QUERY_ROOT] })
        navigate("/login", { replace: true })
    }

    return (
        <div className="hdr-fixo" id="hdrFixo">
            <header className="top">
                <div className="brand">
                    <img id="logo" alt="FINEMP Finanças Empresariais" src={logoUrl} />
                    <div className="who">
                        <div className="eyebrow" id="hGrupo">
                            {firm ? (client ? `${firm.name} › ${client.name}` : firm.name) : "Contabilidade"}
                        </div>
                        <h1 id="hCliente">{company ? company.legalName : "Nenhuma empresa na carteira"}</h1>
                        <div className="meta" id="hMeta">
                            <span>
                                Fechamento: <b>{longMonth(competence)}</b>
                            </span>
                            <span>
                                Base contábil até: <b>{baseUntil ? monthLabel(baseUntil) : "—"}</b>
                            </span>
                        </div>
                    </div>

                    <div className="ctb-hd-acts no-print">
                        <div className="tabs area-seg" role="group" aria-label="Área do sistema">
                            <button type="button" className="pill" onClick={() => navigate("/dashboard")}>
                                Empresa
                            </button>
                            <button type="button" className="pill on" aria-current="page">
                                Contabilidade
                            </button>
                        </div>
                        <button
                            className="hdr-ico"
                            id="btnFiltros"
                            type="button"
                            aria-haspopup="dialog"
                            aria-label="Contabilidade, cliente e empresa"
                            title="Contabilidade, cliente e empresa"
                            onClick={onOpenContext}
                        >
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M3 4h18l-7 8.5V19l-4 2v-8.5z" />
                            </svg>
                        </button>
                        <button type="button" className="tut-btn" onClick={() => void handleLogout()}>
                            Sair
                        </button>
                    </div>
                </div>
                <hr className="rule" />
            </header>

            <div className="pg-row">
                <h2 className="pg-tit" id="pgTit">
                    <span>{current?.module.label ?? ""}</span>
                </h2>
                <HeaderNav current={current} onOpenTab={onOpenTab} />
                <div className="tut-bts no-print">
                    <button
                        className="tut-btn"
                        id="btnImpAba"
                        type="button"
                        title="Imprimir esta aba (ou salvar em PDF)"
                        onClick={() => window.print()}
                    >
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path
                                d="M7 9V3h10v6M7 17H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-2M7 14h10v7H7z"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinejoin="round"
                            />
                        </svg>
                        Imprimir
                    </button>
                </div>
            </div>

            <div className="legend" id="legend">
                {company ? (
                    <>
                        <b>{company.legalName}</b> · competência <b>{monthLabel(competence)}</b> ·{" "}
                        {companyGrant
                            ? "dados conforme o seu acesso a esta empresa"
                            : "sem acesso aos dados desta empresa (somente navegação)"}
                    </>
                ) : (
                    "Nenhuma empresa disponível na sua carteira."
                )}
            </div>
        </div>
    )
}
