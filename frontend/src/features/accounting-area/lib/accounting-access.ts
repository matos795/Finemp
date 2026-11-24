/**
 * Regras de navegação da Área da Contabilidade. Só UX: a autorização real
 * de cada operação continua no backend (grant + permissão + escopo de dados).
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */

/** Schema OrganizationAccess (OpenAPI getCurrentOrganizationAccess). */
export interface OrganizationAccess {
    platformPermissions: string[]
    accountingFirms: {
        accountingFirmId: string
        name: string
        active: boolean
        permissions: string[]
    }[]
}

/** Schema AccountingPortfolio (OpenAPI getAccountingPortfolio, #435). */
export interface AccountingPortfolio {
    accountingFirms: PortfolioFirm[]
}

export interface PortfolioFirm {
    accountingFirmId: string
    name: string
    active: boolean
    scope: "FULL_HIERARCHY" | "GRANTED_COMPANIES"
    clients: PortfolioClient[]
}

export interface PortfolioClient {
    clientId: string
    name: string
    active: boolean
    companies: PortfolioCompany[]
}

export interface PortfolioCompany {
    companyId: string
    legalName: string
}

/**
 * A Área da Contabilidade pertence à equipe da contabilidade: aparece para
 * quem é membro de ao menos uma contabilidade ativa. SYSTEM_ADMIN recebe do
 * backend todas as contabilidades e por isso também entra.
 */
export function canEnterAccountingArea(access: OrganizationAccess | null | undefined): boolean {
    return (access?.accountingFirms ?? []).some((firm) => firm.active)
}

/**
 * Destino depois do login, sem mudar o comportamento de quem tem empresas:
 * continua no Painel da Área da Empresa. Só quem não tem empresa e é membro
 * de contabilidade vai direto para a Área da Contabilidade.
 */
export function homePathFor(
    companyCount: number,
    access: OrganizationAccess | null | undefined,
    accountingHome: string,
): string {
    if (companyCount === 0 && canEnterAccountingArea(access)) {
        return accountingHome
    }

    return "/dashboard"
}

export interface PortfolioSelection {
    firmId?: string
    clientId?: string
    companyId?: string
}

export interface ResolvedSelection {
    firm: PortfolioFirm | null
    client: PortfolioClient | null
    company: PortfolioCompany | null
}

/**
 * Resolve a seleção Contabilidade → Cliente → Empresa só dentro da carteira
 * devolvida pela sessão. Uma escolha guardada que não está mais na carteira
 * (grant revogado, empresa movida) é ignorada e cai na primeira opção válida.
 */
export function resolveSelection(
    portfolio: AccountingPortfolio | null | undefined,
    selection: PortfolioSelection,
): ResolvedSelection {
    const firms = portfolio?.accountingFirms ?? []
    const firm = firms.find((item) => item.accountingFirmId === selection.firmId) ?? firms[0] ?? null
    const clients = firm?.clients ?? []
    const client = clients.find((item) => item.clientId === selection.clientId) ?? clients[0] ?? null
    const companies = client?.companies ?? []
    const company = companies.find((item) => item.companyId === selection.companyId) ?? companies[0] ?? null

    return { firm, client, company }
}
