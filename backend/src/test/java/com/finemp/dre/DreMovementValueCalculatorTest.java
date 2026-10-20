package com.finemp.dre;

import com.finemp.accounting.movement.AccountingMovementSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DreMovementValueCalculatorTest {

    private final DreMovementValueCalculator calculator = new DreMovementValueCalculator();

    @Test
    void shouldKeepDebitPositiveForAccountStartingWithOne() {

        BigDecimal result = calculator.calculate(
                "1.1.01.100",
                AccountingMovementSide.DEBIT,
                new BigDecimal("100.00"));

        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldMakeCreditNegativeForAccountStartingWithOne() {

        BigDecimal result = calculator.calculate(
                "1.1.01.100",
                AccountingMovementSide.CREDIT,
                new BigDecimal("100.00"));

        assertThat(result).isEqualByComparingTo("-100.00");
    }

    @Test
    void shouldMakeDebitNegativeForOtherAccounts() {

        BigDecimal result = calculator.calculate(
                "4.1.01.100",
                AccountingMovementSide.DEBIT,
                new BigDecimal("100.00"));

        assertThat(result).isEqualByComparingTo("-100.00");
    }

    @Test
    void shouldKeepCreditPositiveForOtherAccounts() {

        BigDecimal result = calculator.calculate(
                "3.1.01.100",
                AccountingMovementSide.CREDIT,
                new BigDecimal("100.00"));

        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectBlankAccountKey() {

        assertThatThrownBy(() -> calculator.calculate(
                "",
                AccountingMovementSide.CREDIT,
                new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountKey");
    }

    @Test
    void shouldRejectNullSide() {

        assertThatThrownBy(() -> calculator.calculate(
                "3.1.01.100",
                null,
                new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("side");
    }

    @Test
    void shouldRejectNullAmount() {

        assertThatThrownBy(() -> calculator.calculate(
                "3.1.01.100",
                AccountingMovementSide.CREDIT,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNegativeAmount() {

        assertThatThrownBy(() -> calculator.calculate(
                "3.1.01.100",
                AccountingMovementSide.CREDIT,
                new BigDecimal("-100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNullAccountKey() {

        assertThatThrownBy(() -> calculator.calculate(
                null,
                AccountingMovementSide.CREDIT,
                new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountKey");
    }
}