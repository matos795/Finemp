package com.finemp.accounting.fileimport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingFileImportReceipt(
        UUID accountingSourceId,
        UUID idempotencyKey,
        AccountingFileDataset dataset,
        String fileSha256,
        String fingerprint,
        String layoutProfile,
        LocalDate coveredFrom,
        LocalDate coveredTo,
        long rowCount,
        long publishedCount,
        long skippedCount,
        UUID createdBy,
        Instant committedAt) {
}
