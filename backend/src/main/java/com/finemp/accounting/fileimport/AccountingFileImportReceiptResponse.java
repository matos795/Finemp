package com.finemp.accounting.fileimport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingFileImportReceiptResponse(
        UUID accountingSourceId,
        UUID idempotencyKey,
        LocalDate coveredFrom,
        LocalDate coveredTo,
        long rowCount,
        long publishedCount,
        long skippedCount,
        Instant committedAt) {

    static AccountingFileImportReceiptResponse from(AccountingFileImportReceipt receipt) {
        return new AccountingFileImportReceiptResponse(
                receipt.accountingSourceId(),
                receipt.idempotencyKey(),
                receipt.coveredFrom(),
                receipt.coveredTo(),
                receipt.rowCount(),
                receipt.publishedCount(),
                receipt.skippedCount(),
                receipt.committedAt());
    }
}
