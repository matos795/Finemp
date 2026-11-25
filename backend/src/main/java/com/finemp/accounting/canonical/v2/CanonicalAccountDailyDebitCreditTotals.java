package com.finemp.accounting.canonical.v2;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CanonicalAccountDailyDebitCreditTotals(
        String establishmentCode,
        String sourceAccountId,
        LocalDate day,
        String entryTypeCode,
        BigDecimal debitTotal,
        BigDecimal creditTotal) {
}
