package com.finemp.debt;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DebtAgreementValuesTest {

    @Test
    void monthlySacKeepsInputAndEffectiveRateExplicit() {
        var values = new DebtAgreementValues(
                DebtOrigin.BANK,
                "Capital de giro",
                DebtAmortizationSystem.SAC,
                new BigDecimal("1000.00"),
                3,
                new BigDecimal("3.00"),
                DebtRateInputPeriod.MONTHLY,
                null,
                LocalDate.of(2026, 1, 31));

        assertThat(values.inputRate()).isEqualByComparingTo("3");
        assertThat(values.inputRatePeriod()).isEqualTo(DebtRateInputPeriod.MONTHLY);
        assertThat(values.effectiveMonthlyRate()).isEqualByComparingTo("3.000000000000");
        assertThat(values.fixedInstallmentAmount()).isNull();
    }

    @Test
    void annualSacConvertsToEquivalentMonthlyRate() {
        var values = new DebtAgreementValues(
                DebtOrigin.BANK,
                "Crédito anual",
                DebtAmortizationSystem.SAC,
                new BigDecimal("1000.00"),
                12,
                new BigDecimal("12.6825030131969720661201"),
                DebtRateInputPeriod.ANNUAL,
                null,
                LocalDate.of(2026, 1, 31));

        assertThat(values.effectiveMonthlyRate()).isCloseTo(
                new BigDecimal("1.000000000000"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    void fixedSystemDerivesImpliedRateFromInstallment() {
        var values = new DebtAgreementValues(
                DebtOrigin.SUPPLIER,
                "Parcelamento negociado",
                DebtAmortizationSystem.FIXED,
                new BigDecimal("1000.00"),
                12,
                null,
                DebtRateInputPeriod.IMPLIED,
                new BigDecimal("88.85"),
                LocalDate.of(2026, 1, 31));

        assertThat(values.inputRate()).isNull();
        assertThat(values.inputRatePeriod()).isEqualTo(DebtRateInputPeriod.IMPLIED);
        assertThat(values.effectiveMonthlyRate()).isGreaterThan(BigDecimal.ZERO);
        assertThat(values.fixedInstallmentAmount()).isEqualByComparingTo("88.85");
    }

    @Test
    void fixedSystemRejectsExplicitRate() {
        assertThatThrownBy(() -> new DebtAgreementValues(
                DebtOrigin.BANK,
                "Inválida",
                DebtAmortizationSystem.FIXED,
                new BigDecimal("1000.00"),
                12,
                new BigDecimal("1.00"),
                DebtRateInputPeriod.MONTHLY,
                new BigDecimal("88.85"),
                LocalDate.of(2026, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sacRejectsFixedInstallmentAmount() {
        assertThatThrownBy(() -> new DebtAgreementValues(
                DebtOrigin.BANK,
                "Inválida",
                DebtAmortizationSystem.SAC,
                new BigDecimal("1000.00"),
                12,
                new BigDecimal("1.00"),
                DebtRateInputPeriod.MONTHLY,
                new BigDecimal("88.85"),
                LocalDate.of(2026, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptionAndMoneyAreValidated() {
        assertThatThrownBy(() -> new DebtAgreementValues(
                DebtOrigin.BANK,
                "x",
                DebtAmortizationSystem.SAC,
                new BigDecimal("10.001"),
                3,
                BigDecimal.ZERO,
                DebtRateInputPeriod.MONTHLY,
                null,
                LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
