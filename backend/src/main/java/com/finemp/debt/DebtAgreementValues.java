package com.finemp.debt;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Embeddable
public record DebtAgreementValues(
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        DebtOrigin origin,

        @Column(nullable = false, length = 255)
        String description,

        @Enumerated(EnumType.STRING)
        @Column(name = "amortization_system", nullable = false, length = 20)
        DebtAmortizationSystem amortizationSystem,

        @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
        BigDecimal principalAmount,

        @Column(name = "installment_count", nullable = false)
        int installmentCount,

        @Column(name = "input_rate", precision = 30, scale = 24)
        BigDecimal inputRate,

        @Enumerated(EnumType.STRING)
        @Column(name = "input_rate_period", nullable = false, length = 20)
        DebtRateInputPeriod inputRatePeriod,

        @Column(name = "fixed_installment_amount", precision = 19, scale = 2)
        BigDecimal fixedInstallmentAmount,

        @Column(name = "first_due_date", nullable = false)
        LocalDate firstDueDate,

        @Column(name = "effective_monthly_rate", nullable = false, precision = 30, scale = 12)
        BigDecimal effectiveMonthlyRate) {

    public DebtAgreementValues(
            DebtOrigin origin,
            String description,
            DebtAmortizationSystem amortizationSystem,
            BigDecimal principalAmount,
            int installmentCount,
            BigDecimal inputRate,
            DebtRateInputPeriod inputRatePeriod,
            BigDecimal fixedInstallmentAmount,
            LocalDate firstDueDate) {
        this(
                origin,
                normalizeDescription(description),
                amortizationSystem,
                money(principalAmount),
                installmentCount,
                normalizeInputRate(inputRate),
                inputRatePeriod,
                fixedInstallmentAmount == null ? null : money(fixedInstallmentAmount),
                firstDueDate,
                effectiveRate(
                        amortizationSystem,
                        principalAmount,
                        installmentCount,
                        inputRate,
                        inputRatePeriod,
                        fixedInstallmentAmount));
    }

    public DebtAgreementValues {
        if (origin == null
                || amortizationSystem == null
                || inputRatePeriod == null
                || firstDueDate == null
                || installmentCount < 1
                || installmentCount > 1200) {
            throw invalid();
        }

        description = normalizeDescription(description);
        principalAmount = money(principalAmount);

        if (amortizationSystem == DebtAmortizationSystem.SAC) {
            if (inputRatePeriod == DebtRateInputPeriod.IMPLIED
                    || inputRate == null
                    || fixedInstallmentAmount != null) {
                throw invalid();
            }
        } else {
            if (inputRatePeriod != DebtRateInputPeriod.IMPLIED
                    || inputRate != null
                    || fixedInstallmentAmount == null) {
                throw invalid();
            }
            fixedInstallmentAmount = money(fixedInstallmentAmount);
        }

        if (effectiveMonthlyRate == null || effectiveMonthlyRate.signum() < 0) {
            throw invalid();
        }

        inputRate = normalizeInputRate(inputRate);
        effectiveMonthlyRate = effectiveMonthlyRate
                .setScale(12, RoundingMode.HALF_UP);
    }

    public java.util.List<DebtScheduleCalculator.Installment> schedule() {
        return switch (amortizationSystem) {
            case SAC -> DebtScheduleCalculator.sac(
                    principalAmount,
                    installmentCount,
                    effectiveMonthlyRate,
                    firstDueDate);
            case FIXED -> DebtScheduleCalculator.fixed(
                    principalAmount,
                    installmentCount,
                    fixedInstallmentAmount,
                    firstDueDate);
        };
    }

    private static BigDecimal effectiveRate(
            DebtAmortizationSystem system,
            BigDecimal principal,
            int count,
            BigDecimal inputRate,
            DebtRateInputPeriod period,
            BigDecimal fixedInstallment) {
        if (system == null || period == null) throw invalid();

        if (system == DebtAmortizationSystem.FIXED) {
            if (period != DebtRateInputPeriod.IMPLIED || inputRate != null || fixedInstallment == null) {
                throw invalid();
            }
            return DebtScheduleCalculator.impliedMonthlyPercent(
                    money(principal),
                    money(fixedInstallment),
                    count);
        }

        if (period == DebtRateInputPeriod.IMPLIED || inputRate == null) {
            throw invalid();
        }

        BigDecimal normalized = normalizeInputRate(inputRate);
        return period == DebtRateInputPeriod.ANNUAL
                ? DebtScheduleCalculator.monthlyPercentFromAnnualPercent(normalized)
                : normalized.setScale(12, RoundingMode.HALF_UP);
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.length() > 255) throw invalid();
        String normalized = value.trim();
        if (normalized.length() < 2) throw invalid();
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw invalid();
        try {
            BigDecimal compact = value.stripTrailingZeros();
            if (compact.scale() > 2 || (long) compact.precision() - compact.scale() > 17) {
                throw invalid();
            }
            return compact.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static BigDecimal normalizeInputRate(BigDecimal value) {
        if (value == null) return null;
        if (value.signum() < 0) throw invalid();
        BigDecimal compact = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (compact.scale() < 0) compact = compact.setScale(0);
        if (compact.scale() > 24 || compact.compareTo(new BigDecimal("10000")) > 0) {
            throw invalid();
        }
        return compact;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Dados do acordo de dívida inválidos.");
    }
}
