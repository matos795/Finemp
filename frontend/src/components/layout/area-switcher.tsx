import { NavLink } from "react-router"
import { useOrganizationAccess } from "../../features/accounting-area/hooks/use-accounting-area-queries"
import { canEnterAccountingArea } from "../../features/accounting-area/lib/accounting-access"
import { ACCOUNTING_AREA_HOME } from "../../features/accounting-area/lib/accounting-screens"

/**
 * Seletor Empresa | Contabilidade no cabeçalho da Área da Empresa, no mesmo
 * login. Só aparece para membros de contabilidade; os demais usuários veem o
 * cabeçalho como antes. A autorização real fica no backend.
 */
export function AreaSwitcher() {
    const organizationAccess = useOrganizationAccess()

    if (!canEnterAccountingArea(organizationAccess.data)) {
        return null
    }

    return (
        <div className="f area-f">
            <label>Área</label>
            <div className="seg area-seg" role="group" aria-label="Área do sistema">
                <span className="area-opt on" aria-current="page">
                    Empresa
                </span>
                <NavLink to={ACCOUNTING_AREA_HOME} className="area-opt">
                    Contabilidade
                </NavLink>
            </div>
        </div>
    )
}
