package com.finemp.dre;

import com.finemp.accounting.movement.AccountingMovementSide;

import java.math.BigDecimal;
import java.util.UUID;

public record DreMovementRow(
        UUID accountId,
        String accountKey,
        AccountingMovementSide side,
        BigDecimal amount,
        String dreClassification,
        String dreOpenClassification,
        String ebitdaClassification) {
}