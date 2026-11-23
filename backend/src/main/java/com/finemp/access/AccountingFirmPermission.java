package com.finemp.access;

import java.util.Set;

/**
 * Permissões no escopo de uma Contabilidade. Nenhuma delas concede acesso
 * a dados financeiros/contábeis de Company: isso continua no CompanyAccessGrant.
 */
public enum AccountingFirmPermission {
    /** Consulta todos os Clients e Companies da contabilidade, sem alterar. */
    HIERARCHY_VIEW,
    /** Administra Clients e Companies; também satisfaz leitura. */
    HIERARCHY_MANAGE,
    /** Consulta AccountingSources no escopo autorizado, sem alterar. */
    ACCOUNTING_SOURCE_VIEW,
    /** Administra AccountingSources; também satisfaz leitura. */
    ACCOUNTING_SOURCE_MANAGE,
    USER_ACCESS_MANAGE,
    CONNECTOR_MANAGE;

    /** Qualquer uma destas permite ler a hierarquia (Clients e Companies). */
    public static final Set<AccountingFirmPermission> HIERARCHY_READ = Set.of(HIERARCHY_VIEW, HIERARCHY_MANAGE);

    /** Qualquer uma destas permite ler AccountingSources. */
    public static final Set<AccountingFirmPermission> ACCOUNTING_SOURCE_READ =
            Set.of(ACCOUNTING_SOURCE_VIEW, ACCOUNTING_SOURCE_MANAGE);
}
