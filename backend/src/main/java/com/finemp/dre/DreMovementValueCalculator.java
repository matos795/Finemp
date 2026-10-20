package com.finemp.dre;

import com.finemp.accounting.movement.AccountingMovementSide;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DreMovementValueCalculator {

    public BigDecimal calculate(
            String accountKey,
            AccountingMovementSide side,
            BigDecimal amount) {

        validate(accountKey, side, amount);

        boolean accountStartsWithOne = accountKey.startsWith("1");

        if (accountStartsWithOne) {
            return side == AccountingMovementSide.DEBIT
                    ? amount
                    : amount.negate();
        }

        return side == AccountingMovementSide.CREDIT
                ? amount
                : amount.negate();
    }

    private void validate(
            String accountKey,
            AccountingMovementSide side,
            BigDecimal amount) {

        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalArgumentException("accountKey must not be blank");
        }

        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }

        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}