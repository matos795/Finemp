package com.finemp.security;

import java.util.List;
import java.util.UUID;

/**
 * Carteira operacional: a hierarquia AccountingFirm → Client → Company que o
 * usuário da sessão pode navegar. Não traz permissões de dados: o acesso aos
 * dados de cada Company continua definido pelos grants de /api/auth/me.
 */
public record AccountingPortfolioResponse(List<AccountingFirmNode> accountingFirms) {

    public AccountingPortfolioResponse {
        accountingFirms = List.copyOf(accountingFirms);
    }

    /** Como a contabilidade foi incluída na carteira. */
    public enum Scope {
        /** SYSTEM_ADMIN, HIERARCHY_VIEW ou HIERARCHY_MANAGE: toda a hierarquia da contabilidade. */
        FULL_HIERARCHY,
        /** Somente Companies com CompanyAccessGrant do usuário e seus ancestrais. */
        GRANTED_COMPANIES
    }

    public record AccountingFirmNode(UUID accountingFirmId, String name, boolean active, Scope scope,
                                     List<ClientNode> clients) {
        public AccountingFirmNode {
            clients = List.copyOf(clients);
        }
    }

    public record ClientNode(UUID clientId, String name, boolean active, List<CompanyNode> companies) {
        public ClientNode {
            companies = List.copyOf(companies);
        }
    }

    public record CompanyNode(UUID companyId, String legalName) { }
}
