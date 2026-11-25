package com.finemp.accounting.canonical.v2;

import java.math.BigDecimal;
import java.time.YearMonth;

public record CanonicalAccountMonthlyDebitCreditTotals(
        String establishmentCode,
        String sourceAccountId,
        YearMonth month,
        String entryTypeCode,
        BigDecimal debitTotal,
        BigDecimal creditTotal) {
}
