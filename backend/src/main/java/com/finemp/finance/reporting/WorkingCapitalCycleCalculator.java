package com.finemp.finance.reporting;

import com.finemp.finance.category.FinancialDirection;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pure F09 calculation. Persistence selection belongs to the report service.
 */
public final class WorkingCapitalCycleCalculator {

    private static final int DAYS_SCALE = 2;
    private static final int DAILY_SCALE = 10;
    private static final MathContext MC = MathContext.DECIMAL128;

    private WorkingCapitalCycleCalculator() {
    }

    public static Result calculate(
            List<Entry> entries,
            BigDecimal inventoryDays,
            int periodDays) {
        if (entries == null || periodDays < 1) {
            throw new IllegalArgumentException("Base do ciclo financeiro inválida.");
        }
        if (inventoryDays != null && inventoryDays.signum() < 0) {
            throw new IllegalArgumentException("PME inválido.");
        }

        var receivables = weighted(entries, FinancialDirection.INCOME);
        var payables = weighted(entries, FinancialDirection.EXPENSE);
        var normalizedInventory = inventoryDays == null
                ? null
                : inventoryDays.setScale(DAYS_SCALE, RoundingMode.HALF_UP);

        BigDecimal operating = receivables == null || normalizedInventory == null
                ? null
                : receivables.add(normalizedInventory).setScale(DAYS_SCALE, RoundingMode.HALF_UP);
        BigDecimal financial = operating == null || payables == null
                ? null
                : operating.subtract(payables).setScale(DAYS_SCALE, RoundingMode.HALF_UP);

        BigDecimal revenue = entries.stream()
                .filter(entry -> entry.direction() == FinancialDirection.INCOME)
                .map(Entry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyRevenue = revenue.divide(
                BigDecimal.valueOf(periodDays),
                DAILY_SCALE,
                RoundingMode.HALF_UP);

        BigDecimal cycleWorkingCapital = financial == null
                ? null
                : dailyRevenue.multiply(financial, MC).setScale(2, RoundingMode.HALF_UP);

        return new Result(
                receivables,
                payables,
                normalizedInventory,
                operating,
                financial,
                dailyRevenue,
                cycleWorkingCapital);
    }

    private static BigDecimal weighted(
            List<Entry> entries,
            FinancialDirection direction) {
        BigDecimal weightedDays = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;

        for (Entry entry : entries) {
            if (entry == null
                    || entry.direction() != direction
                    || entry.amount() == null
                    || entry.amount().signum() <= 0
                    || entry.documentDate() == null
                    || entry.dueDate() == null) {
                continue;
            }

            LocalDate end = entry.paymentDate() == null
                    ? entry.dueDate()
                    : entry.paymentDate();
            long days = ChronoUnit.DAYS.between(entry.documentDate(), end);

            weightedDays = weightedDays.add(
                    entry.amount().multiply(BigDecimal.valueOf(days), MC));
            weight = weight.add(entry.amount());
        }

        if (weight.signum() == 0) {
            return null;
        }

        return weightedDays.divide(weight, DAYS_SCALE, RoundingMode.HALF_UP);
    }

    public record Entry(
            FinancialDirection direction,
            BigDecimal amount,
            LocalDate documentDate,
            LocalDate dueDate,
            LocalDate paymentDate) {
    }

    public record Result(
            BigDecimal receivablesDays,
            BigDecimal payablesDays,
            BigDecimal inventoryDays,
            BigDecimal operatingCycleDays,
            BigDecimal financialCycleDays,
            BigDecimal dailyRevenue,
            BigDecimal cycleWorkingCapital) {
    }
}
