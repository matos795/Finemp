package com.finemp.accounting.v2read;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record AccountingDebitCreditTotalsRow(
        String establishmentCode,
        String sourceAccountId,
        UUID accountId,
        LocalDate periodStart,
        String entryTypeCode,
        BigDecimal debitTotal,
        BigDecimal creditTotal) {}
