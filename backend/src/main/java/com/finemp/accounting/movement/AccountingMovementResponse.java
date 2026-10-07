package com.finemp.accounting.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingMovementResponse(
        UUID id,
        LocalDate accountingDate,
        String accountDescription,
        String accountKey,
        AccountingMovementSide side,
        BigDecimal amount,
        String history,
        String sourceEntryKey
) {

    public static AccountingMovementResponse from(
            AccountingMovement movement
    ) {
        return new AccountingMovementResponse(
                movement.getId(),
                movement.getAccountingDate(),
                movement.getAccount().getDescription(),
                movement.getAccount().getAccountKey(),
                movement.getSide(),
                movement.getAmount(),
                movement.getHistory(),
                movement.getSourceEntryKey()
        );
    }
}