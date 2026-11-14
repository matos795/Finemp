package com.finemp.assets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class AssetDepreciationCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private AssetDepreciationCalculator() {
    }

    public static BigDecimal monthlyDepreciation(Input input) {
        require(input);

        if (!input.depreciable()) {
            return ZERO_MONEY;
        }

        return depreciableBase(input)
                .divide(
                        BigDecimal.valueOf(input.usefulLifeMonths()),
                        2,
                        RoundingMode.HALF_UP);
    }

    public static BigDecimal depreciationForMonth(
            Input input,
            YearMonth month) {
        require(input);
        if (month == null || !input.depreciable()) {
            if (month == null) throw invalid();
            return ZERO_MONEY;
        }

        YearMonth first = YearMonth.from(input.effectiveDepreciationStartDate());
        YearMonth last = lastDepreciableMonth(input);

        if (month.isBefore(first) || month.isAfter(last)) {
            return ZERO_MONEY;
        }

        return monthlyDepreciation(input);
    }

    public static BigDecimal accumulatedDepreciation(
            Input input,
            LocalDate asOf) {
        require(input);
        if (asOf == null) throw invalid();

        if (!input.depreciable()) {
            return ZERO_MONEY;
        }

        YearMonth first = YearMonth.from(input.effectiveDepreciationStartDate());
        YearMonth requested = YearMonth.from(asOf);

        if (requested.isBefore(first)) {
            return ZERO_MONEY;
        }

        YearMonth end = min(requested, lastDepreciableMonth(input));
        long months = ChronoUnit.MONTHS.between(first, end) + 1;

        if (months <= 0) {
            return ZERO_MONEY;
        }

        BigDecimal accumulated = monthlyDepreciation(input)
                .multiply(BigDecimal.valueOf(months))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal base = depreciableBase(input);
        return accumulated.compareTo(base) > 0
                ? base
                : accumulated;
    }

    public static BigDecimal bookValue(
            Input input,
            LocalDate asOf) {
        require(input);
        if (asOf == null) throw invalid();

        return input.acquisitionValue()
                .subtract(accumulatedDepreciation(input, asOf))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal bookValueAtDisposal(Input input) {
        require(input);
        if (input.disposalDate() == null) throw invalid();

        return bookValue(input, input.disposalDate());
    }

    public static BigDecimal disposalGainLoss(
            Input input,
            BigDecimal disposalAmount) {
        require(input);
        if (input.disposalDate() == null) throw invalid();

        BigDecimal amount = money(disposalAmount);
        return amount
                .subtract(bookValueAtDisposal(input))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal depreciableBase(Input input) {
        BigDecimal retained = input.acquisitionValue()
                .multiply(input.residualPercent())
                .divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);

        return input.acquisitionValue()
                .subtract(retained)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static YearMonth lastDepreciableMonth(Input input) {
        YearMonth first = YearMonth.from(input.effectiveDepreciationStartDate());
        YearMonth byLife = first.plusMonths(input.usefulLifeMonths() - 1L);

        if (input.disposalDate() == null) {
            return byLife;
        }

        return min(byLife, YearMonth.from(input.disposalDate()));
    }

    private static YearMonth min(YearMonth left, YearMonth right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static void require(Input input) {
        if (input == null) throw invalid();
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw invalid();

        try {
            BigDecimal compact = value.signum() == 0
                    ? BigDecimal.ZERO
                    : value.stripTrailingZeros();

            if (compact.scale() > 2) throw invalid();
            return compact.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Parâmetros patrimoniais inválidos.");
    }

    public record Input(
            BigDecimal acquisitionValue,
            int usefulLifeMonths,
            BigDecimal residualPercent,
            boolean depreciable,
            LocalDate acquisitionDate,
            LocalDate depreciationStartDate,
            LocalDate disposalDate) {

        public Input {
            acquisitionValue = money(acquisitionValue);

            if (usefulLifeMonths < 0
                    || depreciable && usefulLifeMonths < 1
                    || residualPercent == null
                    || acquisitionDate == null
                    || depreciationStartDate != null
                    && depreciationStartDate.isBefore(acquisitionDate)
                    || residualPercent.signum() < 0
                    || residualPercent.compareTo(ONE_HUNDRED) > 0
                    || disposalDate != null
                    && disposalDate.isBefore(acquisitionDate)) {
                throw invalid();
            }

            residualPercent = residualPercent.stripTrailingZeros();
            if (residualPercent.scale() < 0) {
                residualPercent = residualPercent.setScale(0);
            }
        }

        LocalDate effectiveDepreciationStartDate() {
            return depreciationStartDate == null
                    ? acquisitionDate
                    : depreciationStartDate;
        }
    }
}
