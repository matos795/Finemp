package com.finemp.accounting.fileimport;

import com.finemp.accounting.movement.AccountingMovementSide;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GeneralLedgerPreviewRow(
        int sourceRow,
        String sourceAccountId,
        String accountDescription,
        LocalDate accountingDate,
        String sourceEntryKey,
        AccountingMovementSide side,
        BigDecimal amount,
        String history,
        BigDecimal reportedBalance) {
}
