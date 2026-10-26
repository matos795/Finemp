package com.finemp.accounting.classification;

import java.util.List;

public record AccountingAccountClassificationPageResponse(
        List<AccountingAccountClassificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long totalAccounts,
        long pendingAccounts,
        long classifiedAccounts) {
}