import type { ReactNode } from "react"
import { Link } from "react-router"
import { QueryError } from "../../../components/finemp/query-error"
import { useAccountingPortfolio, useOrganizationAccess } from "../hooks/use-accounting-area-queries"
import { canEnterAccountingArea } from "../lib/accounting-access"
import { AccountingAreaLayout } from "./accounting-area-layout"
import { AccountingContextProvider } from "./accounting-context-provider"

function Shell({ children }: { children: ReactNode }) {
    return (
        <div className="fin min-h-screen">
            <div className="wrap">{children}</div>
        </div>
    )
}

/**
 * Entrada da Área da Contabilidade: exige vínculo com ao menos uma
 * contabilidade ativa (organization-access) e carrega a carteira da sessão.
 * É só navegação: cada dado continua autorizado pelo backend.
 */
export function RequireAccountingArea() {
    const organizationAccess = useOrganizationAccess()
    const allowed = organizationAccess.isSuccess && canEnterAccountingArea(organizationAccess.data)
    const portfolio = useAccountingPortfolio(allowed)

    if (organizationAccess.isLoading || (allowed && portfolio.isLoading)) {
        return (
            <div className="fin min-h-screen">
                <div className="empty">Carregando…</div>
            </div>
        )
    }

    if (organizationAccess.isError || portfolio.isError) {
        const error = organizationAccess.error ?? portfolio.error

        return (
            <Shell>
                <QueryError
                    error={error}
                    fallback="Não foi possível carregar a Área da Contabilidade."
                    onRetry={() => {
                        void organizationAccess.refetch()
                        void portfolio.refetch()
                    }}
                />
            </Shell>
        )
    }

    if (!allowed || !portfolio.data) {
        return (
            <Shell>
                <div className="alert i" style={{ marginTop: 24 }}>
                    A Área da Contabilidade é exclusiva de membros de uma contabilidade. Seu usuário não está
                    vinculado a nenhuma contabilidade ativa.{" "}
                    <Link to="/dashboard" style={{ color: "var(--gold)" }}>
                        Ir para a Área da Empresa
                    </Link>
                </div>
            </Shell>
        )
    }

    return (
        <AccountingContextProvider portfolio={portfolio.data}>
            <AccountingAreaLayout />
        </AccountingContextProvider>
    )
}
