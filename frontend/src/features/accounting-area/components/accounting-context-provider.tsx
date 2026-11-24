import { useState, type ReactNode } from "react"
import { useCompanyAccess } from "../../companies/company-access-context"
import { AccountingContext } from "../accounting-context"
import { resolveSelection, type AccountingPortfolio, type PortfolioSelection } from "../lib/accounting-access"

const SELECTION_PREFERENCE_KEY = "finemp.ui.ctb-selection"

/** Última seleção: só preferência de navegação, sempre revalidada contra a carteira. */
function readSelection(): PortfolioSelection {
    try {
        const raw = localStorage.getItem(SELECTION_PREFERENCE_KEY)
        const parsed: unknown = raw ? JSON.parse(raw) : null

        return parsed && typeof parsed === "object" ? (parsed as PortfolioSelection) : {}
    } catch {
        return {}
    }
}

function saveSelection(selection: PortfolioSelection) {
    try {
        localStorage.setItem(SELECTION_PREFERENCE_KEY, JSON.stringify(selection))
    } catch {
        // Preferência opcional.
    }
}

interface AccountingContextProviderProps {
    portfolio: AccountingPortfolio
    children: ReactNode
}

export function AccountingContextProvider({ portfolio, children }: AccountingContextProviderProps) {
    const { user } = useCompanyAccess()
    const [selection, setSelection] = useState<PortfolioSelection>(readSelection)
    const [competence, setCompetence] = useState<string | null>(null)
    const { firm, client, company } = resolveSelection(portfolio, selection)

    function update(next: PortfolioSelection) {
        setSelection(next)
        saveSelection(next)
        setCompetence(null)
    }

    const companyGrant =
        company === null ? null : user.companies.find((access) => access.id === company.companyId) ?? null

    return (
        <AccountingContext.Provider
            value={{
                portfolio,
                firm,
                client,
                company,
                selectFirm: (firmId) => update({ firmId }),
                selectClient: (clientId) => update({ firmId: firm?.accountingFirmId, clientId }),
                selectCompany: (companyId) =>
                    update({ firmId: firm?.accountingFirmId, clientId: client?.clientId, companyId }),
                companyGrant,
                competence,
                setCompetence,
            }}
        >
            {children}
        </AccountingContext.Provider>
    )
}
