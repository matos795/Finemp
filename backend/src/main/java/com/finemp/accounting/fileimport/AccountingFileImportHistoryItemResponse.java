package com.finemp.accounting.fileimport;

import java.time.Instant;
import java.time.LocalDate;

public record AccountingFileImportHistoryItemResponse(
        LocalDate coveredFrom,
        LocalDate coveredTo,
        long rowCount,
        long publishedCount,
        long skippedCount,
        Instant committedAt) {

    static AccountingFileImportHistoryItemResponse from(AccountingFileImportReceipt receipt) {
        return new AccountingFileImportHistoryItemResponse(
                receipt.coveredFrom(),
                receipt.coveredTo(),
                receipt.rowCount(),
                receipt.publishedCount(),
                receipt.skippedCount(),
                receipt.committedAt());
    }
}
