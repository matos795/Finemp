package com.finemp.accounting.classification;

import java.util.List;

/**
 * Página do plano de contas conhecido pelo FINEMP. {@code universe} é sempre
 * IMPORTED_ACCOUNTS: só contas que vieram nos movimentos sincronizados; contas
 * sem movimento na origem não aparecem (não é o plano completo).
 */
public record ImportedAccountsPageResponse(
        String universe,
        List<AccountingAccountClassificationResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        ImportedAccountsSummary summary) {

    public static final String IMPORTED_ACCOUNTS = "IMPORTED_ACCOUNTS";

    public ImportedAccountsPageResponse {
        items = List.copyOf(items);
    }
}
