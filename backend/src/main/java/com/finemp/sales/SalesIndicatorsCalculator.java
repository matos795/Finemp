package com.finemp.sales;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class SalesIndicatorsCalculator {

    private SalesIndicatorsCalculator() { }

    public static Indicators calculate(List<OpportunityMetric> opportunities) {
        if (opportunities == null) throw new IllegalArgumentException("Oportunidades inválidas.");

        long won = opportunities.stream().filter(o -> o.status() == OpportunityStatus.WON).count();
        long lost = opportunities.stream().filter(o -> o.status() == OpportunityStatus.LOST).count();
        long decided = won + lost;

        BigDecimal wonValue = opportunities.stream()
                .filter(o -> o.status() == OpportunityStatus.WON)
                .map(OpportunityMetric::monthlyAmount)
                .reduce(zero(), BigDecimal::add);

        BigDecimal weightedOpen = opportunities.stream()
                .filter(o -> o.status() == OpportunityStatus.OPEN)
                .map(o -> o.monthlyAmount()
                        .multiply(BigDecimal.valueOf(o.probability()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .reduce(zero(), BigDecimal::add);

        BigDecimal cycleSum = opportunities.stream()
                .filter(o -> o.status() == OpportunityStatus.WON && o.wonAt() != null)
                .map(o -> BigDecimal.valueOf(
                        ChronoUnit.DAYS.between(
                                o.createdAt().atZone(ZoneOffset.UTC).toLocalDate(),
                                o.wonAt().atZone(ZoneOffset.UTC).toLocalDate())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long cycleCount = opportunities.stream()
                .filter(o -> o.status() == OpportunityStatus.WON && o.wonAt() != null)
                .count();

        return new Indicators(
                won,
                lost,
                ratio(won, decided),
                won == 0 ? zero() : wonValue.divide(BigDecimal.valueOf(won), 2, RoundingMode.HALF_UP),
                cycleCount == 0 ? zero() : cycleSum.divide(BigDecimal.valueOf(cycleCount), 2, RoundingMode.HALF_UP),
                weightedOpen,
                wonValue.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.UNNECESSARY));
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) return zero();
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public record OpportunityMetric(
            OpportunityStatus status,
            BigDecimal monthlyAmount,
            int probability,
            Instant createdAt,
            Instant wonAt) {
        public OpportunityMetric {
            if (status == null || monthlyAmount == null || probability < 0 || probability > 100 || createdAt == null) {
                throw new IllegalArgumentException("Métrica comercial inválida.");
            }
        }
    }

    public record Indicators(
            long wonCount,
            long lostCount,
            BigDecimal conversionRate,
            BigDecimal averageWonTicket,
            BigDecimal averageSalesCycleDays,
            BigDecimal weightedOpenPipeline,
            BigDecimal annualizedWonValue) {
    }
}
