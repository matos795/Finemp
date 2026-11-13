package com.finemp.debt;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class DebtScheduleCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final MathContext MC = new MathContext(40, RoundingMode.HALF_UP);
    private static final int RATE_SCALE = 12;

    private DebtScheduleCalculator() {
    }

    public static List<Installment> sac(
            BigDecimal principal,
            int installments,
            BigDecimal monthlyRatePercent,
            LocalDate firstDueDate) {

        BigDecimal normalizedPrincipal = money(principal);
        BigDecimal rate = ratePercent(monthlyRatePercent);
        if (installments < 1 || installments > 1200 || firstDueDate == null) {
            throw invalid();
        }

        BigDecimal regularAmortization = normalizedPrincipal.divide(
                BigDecimal.valueOf(installments),
                2,
                RoundingMode.HALF_UP);

        BigDecimal balance = normalizedPrincipal;
        var rows = new ArrayList<Installment>(installments);

        for (int index = 1; index <= installments; index++) {
            BigDecimal amortization = index == installments
                    ? balance
                    : regularAmortization.min(balance);
            BigDecimal interest = balance
                    .multiply(rate, MC)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal payment = amortization.add(interest)
                    .setScale(2, RoundingMode.UNNECESSARY);

            balance = balance.subtract(amortization)
                    .setScale(2, RoundingMode.UNNECESSARY);

            rows.add(new Installment(
                    index,
                    dueDate(firstDueDate, index - 1),
                    amortization.setScale(2, RoundingMode.UNNECESSARY),
                    interest,
                    payment,
                    balance));
        }

        return List.copyOf(rows);
    }

    /**
     * Effective annual percentage -> equivalent effective monthly percentage.
     * Uses a decimal Newton root instead of binary floating point.
     */
    public static BigDecimal monthlyPercentFromAnnualPercent(BigDecimal annualPercent) {
        BigDecimal annual = ratePercent(annualPercent);
        if (annual.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }

        BigDecimal annualFactor = ONE.add(annual.divide(HUNDRED, MC));
        BigDecimal monthlyFactor = nthRoot(annualFactor, 12);

        return monthlyFactor.subtract(ONE)
                .multiply(HUNDRED, MC)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Implied fixed-installment monthly percentage.
     * Mirrors the reference search interval [0, 1] as a monthly decimal rate.
     * If even zero interest cannot produce a positive solution, returns zero.
     */
    public static BigDecimal impliedMonthlyPercent(
            BigDecimal principal,
            BigDecimal installment,
            int installments) {

        BigDecimal normalizedPrincipal = money(principal);
        if (installment == null
                || installment.signum() <= 0
                || installments < 1
                || installments > 1200) {
            throw invalid();
        }

        BigDecimal totalAtZero = installment.multiply(BigDecimal.valueOf(installments), MC);
        if (totalAtZero.compareTo(normalizedPrincipal) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal low = BigDecimal.ZERO;
        BigDecimal high = BigDecimal.ONE;

        for (int i = 0; i < 200; i++) {
            BigDecimal mid = low.add(high).divide(BigDecimal.valueOf(2), MC);
            BigDecimal presentValue = annuityPresentValue(
                    installment,
                    installments,
                    mid);

            if (presentValue.compareTo(normalizedPrincipal) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return low.add(high)
                .divide(BigDecimal.valueOf(2), MC)
                .multiply(HUNDRED, MC)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal annuityPresentValue(
            BigDecimal installment,
            int installments,
            BigDecimal monthlyRate) {
        if (monthlyRate.signum() == 0) {
            return installment.multiply(BigDecimal.valueOf(installments), MC);
        }

        BigDecimal factor = ONE.add(monthlyRate).pow(installments, MC);
        BigDecimal discount = ONE.divide(factor, MC);
        return installment
                .multiply(ONE.subtract(discount), MC)
                .divide(monthlyRate, MC);
    }

    private static BigDecimal nthRoot(BigDecimal value, int n) {
        BigDecimal estimate = ONE;
        BigDecimal divisor = BigDecimal.valueOf(n);
        BigDecimal nMinusOne = BigDecimal.valueOf(n - 1L);

        for (int i = 0; i < 100; i++) {
            BigDecimal denominator = estimate.pow(n - 1, MC);
            BigDecimal next = estimate
                    .multiply(nMinusOne, MC)
                    .add(value.divide(denominator, MC), MC)
                    .divide(divisor, MC);

            if (next.subtract(estimate).abs().compareTo(new BigDecimal("1E-35")) < 0) {
                return next;
            }
            estimate = next;
        }

        return estimate;
    }

    private static LocalDate dueDate(LocalDate firstDueDate, int monthsAfterFirst) {
        YearMonth target = YearMonth.from(firstDueDate).plusMonths(monthsAfterFirst);
        int anchorDay = firstDueDate.getDayOfMonth();
        return target.atDay(Math.min(anchorDay, target.lengthOfMonth()));
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw invalid();
        }
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

    private static BigDecimal ratePercent(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw invalid();
        }
        BigDecimal compact = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (compact.scale() < 0) compact = compact.setScale(0);
        if (compact.compareTo(new BigDecimal("10000")) > 0 || compact.scale() > 12) {
            throw invalid();
        }
        return compact;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros financeiros da dívida inválidos.");
    }

    public record Installment(
            int number,
            LocalDate dueDate,
            BigDecimal amortization,
            BigDecimal interest,
            BigDecimal payment,
            BigDecimal balance) {
    }
}
