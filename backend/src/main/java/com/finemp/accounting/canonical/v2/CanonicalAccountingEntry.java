package com.finemp.accounting.canonical.v2;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CanonicalAccountingEntry(
        String sourceEntryKey,
        LocalDate accountingDate,
        String debitAccountId,
        String creditAccountId,
        BigDecimal amount,
        String establishmentCode,
        String historyText) {
}
