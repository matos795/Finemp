import { createContext, useContext } from "react"
import type { CompanyAccess } from "../auth/types/auth.types"
import type { AccountingPortfolio, PortfolioClient, PortfolioCompany, PortfolioFirm } from "./lib/accounting-access"

/**
 * Contexto da Área da Contabilidade: AccountingFirm → Client → Company.
 *
 * A hierarquia vem só de /api/auth/accounting-portfolio (identidade da sessão).
 * `companyGrant` é o grant da empresa em /api/auth/me, quando existir: ele, e
 * não a seleção, define o acesso aos dados contábeis da empresa.
 */
export interface AccountingContextValue {
    portfolio: AccountingPortfolio
    firm: PortfolioFirm | null
    client: PortfolioClient | null
    company: PortfolioCompany | null
    selectFirm: (firmId: string) => void
    selectClient: (clientId: string) => void
    selectCompany: (companyId: string) => void
    companyGrant: CompanyAccess | null
    /** Competência em fechamento ("AAAA-MM"); null = padrão do protótipo (mês seguinte à base). */
    competence: string | null
    setCompetence: (competence: string) => void
}

export const AccountingContext = createContext<AccountingContextValue | null>(null)

export function useAccountingContext() {
    const context = useContext(AccountingContext)

    if (!context) {
        throw new Error("useAccountingContext must be used inside AccountingContextProvider")
    }

    return context
}
