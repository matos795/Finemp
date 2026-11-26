package com.finemp.accounting.fileimport;

import com.finemp.accounting.movement.AccountingMovementSide;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GeneralLedgerParsedMovement(
        int sourceRow,
        String sourceAccountId,
        String accountDescription,
        LocalDate accountingDate,
        String sourceEntryKey,
        AccountingMovementSide side,
        BigDecimal amount,
        String history,
        BigDecimal reportedBalance) {

    GeneralLedgerPreviewRow toPreviewRow() {
        return new GeneralLedgerPreviewRow(
                sourceRow,
                sourceAccountId,
                accountDescription,
                accountingDate,
                sourceEntryKey,
                side,
                amount,
                history,
                reportedBalance);
    }
}
