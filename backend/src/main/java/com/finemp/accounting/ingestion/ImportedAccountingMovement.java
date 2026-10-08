package com.finemp.accounting.ingestion;

import com.finemp.accounting.movement.AccountingMovementSide;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportedAccountingMovement(
        String sourceEntryKey,
        LocalDate accountingDate,
        String externalAccountId,
        String accountKey,
        String accountDescription,
        AccountingMovementSide side,
        BigDecimal amount,
        String history) {
}