import { Navigate } from "react-router"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useOrganizationAccess } from "../hooks/use-accounting-area-queries"
import { homePathFor } from "../lib/accounting-access"
import { ACCOUNTING_AREA_HOME } from "../lib/accounting-screens"

/**
 * Rota inicial depois do login. Quem tem empresa continua indo para o Painel
 * (comportamento existente). Quem não tem empresa e é membro de uma
 * contabilidade vai para a Área da Contabilidade.
 */
export function AreaHomeRedirect() {
    const { user } = useCompanyAccess()
    const organizationAccess = useOrganizationAccess()

    if (user.companies.length === 0 && organizationAccess.isLoading) {
        return (
            <div className="fin min-h-screen">
                <div className="empty">Carregando…</div>
            </div>
        )
    }

    return <Navigate to={homePathFor(user.companies.length, organizationAccess.data, ACCOUNTING_AREA_HOME)} replace />
}
