package com.finemp.sales.indicators;

import com.finemp.sales.opportunity.OpportunityOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SalesIndicatorsCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SalesIndicatorsCalculator() {
    }

    public static SalesIndicators calculate(
            List<SalesOpportunityMetric> opportunities,
            LocalDate today) {

        if (opportunities == null || today == null) {
            throw new IllegalArgumentException("Base comercial inválida.");
        }

        int wonCount = 0;
        int lostCount = 0;
        int openCount = 0;
        BigDecimal wonValue = BigDecimal.ZERO;
        BigDecimal cycleDays = BigDecimal.ZERO;

        BigDecimal pipelineNominal = BigDecimal.ZERO;
        BigDecimal pipelineWeighted = BigDecimal.ZERO;
        BigDecimal pipelineAnnualized = BigDecimal.ZERO;
        BigDecimal pipelineWeightedAnnualized = BigDecimal.ZERO;

        Map<YearMonth, ForecastAccumulator> forecast = new TreeMap<>();
        var currentMonth = YearMonth.from(today);

        for (var opportunity : opportunities) {
            if (opportunity == null) {
                throw new IllegalArgumentException("Oportunidade comercial inválida.");
            }

            switch (opportunity.outcome()) {
                case WON -> {
                    wonCount++;
                    wonValue = wonValue.add(opportunity.amount());
                    cycleDays = cycleDays.add(BigDecimal.valueOf(
                            ChronoUnit.DAYS.between(
                                    opportunity.openedOn(),
                                    opportunity.closedOn())));
                }
                case LOST -> lostCount++;
                case OPEN -> {
                    openCount++;
                    var annualized = opportunity.annualizedAmount();
                    var weighted = weighted(
                            opportunity.amount(),
                            opportunity.probability());
                    var weightedAnnualized = weighted(
                            annualized,
                            opportunity.probability());

                    pipelineNominal = pipelineNominal.add(opportunity.amount());
                    pipelineWeighted = pipelineWeighted.add(weighted);
                    pipelineAnnualized = pipelineAnnualized.add(annualized);
                    pipelineWeightedAnnualized =
                            pipelineWeightedAnnualized.add(weightedAnnualized);

                    var expectedMonth = YearMonth.from(
                            opportunity.expectedCloseOn());
                    var bucketMonth = expectedMonth.isBefore(currentMonth)
                            ? currentMonth
                            : expectedMonth;

                    forecast.computeIfAbsent(
                                    bucketMonth,
                                    ignored -> new ForecastAccumulator())
                            .add(
                                    opportunity.amount(),
                                    weighted,
                                    annualized,
                                    weightedAnnualized);
                }
            }
        }

        var closedCount = wonCount + lostCount;

        BigDecimal ticket = wonCount == 0
                ? null
                : money(wonValue.divide(
                        BigDecimal.valueOf(wonCount),
                        2,
                        RoundingMode.HALF_UP));

        BigDecimal conversion = closedCount == 0
                ? null
                : BigDecimal.valueOf(wonCount)
                        .divide(
                                BigDecimal.valueOf(closedCount),
                                4,
                                RoundingMode.HALF_UP);

        BigDecimal averageCycle = wonCount == 0
                ? null
                : cycleDays.divide(
                        BigDecimal.valueOf(wonCount),
                        4,
                        RoundingMode.HALF_UP);

        List<SalesForecastBucket> buckets = new ArrayList<>();
        forecast.forEach((month, values) ->
                buckets.add(values.toBucket(month)));

        return new SalesIndicators(
                wonCount,
                lostCount,
                openCount,
                money(wonValue),
                ticket,
                conversion,
                averageCycle,
                money(pipelineNominal),
                money(pipelineWeighted),
                money(pipelineAnnualized),
                money(pipelineWeightedAnnualized),
                buckets);
    }

    private static BigDecimal weighted(
            BigDecimal amount,
            int probability) {

        return money(amount
                .multiply(BigDecimal.valueOf(probability))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class ForecastAccumulator {
        private BigDecimal nominal = BigDecimal.ZERO;
        private BigDecimal weighted = BigDecimal.ZERO;
        private BigDecimal annualized = BigDecimal.ZERO;
        private BigDecimal weightedAnnualized = BigDecimal.ZERO;

        void add(
                BigDecimal nominal,
                BigDecimal weighted,
                BigDecimal annualized,
                BigDecimal weightedAnnualized) {
            this.nominal = this.nominal.add(nominal);
            this.weighted = this.weighted.add(weighted);
            this.annualized = this.annualized.add(annualized);
            this.weightedAnnualized =
                    this.weightedAnnualized.add(weightedAnnualized);
        }

        SalesForecastBucket toBucket(YearMonth month) {
            return new SalesForecastBucket(
                    month.toString(),
                    money(nominal),
                    money(weighted),
                    money(annualized),
                    money(weightedAnnualized));
        }
    }
}
