package com.finemp.sales;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesIndicatorsCalculatorTest {

    @Test
    void calculatesConfirmedF30RulesWithBigDecimal() {
        var indicators = SalesIndicatorsCalculator.calculate(List.of(
                new SalesIndicatorsCalculator.OpportunityMetric(
                        OpportunityStatus.WON, new BigDecimal("1000.00"), 100,
                        Instant.parse("2026-11-01T00:00:00Z"),
                        Instant.parse("2026-11-11T00:00:00Z")),
                new SalesIndicatorsCalculator.OpportunityMetric(
                        OpportunityStatus.WON, new BigDecimal("2000.00"), 100,
                        Instant.parse("2026-11-01T00:00:00Z"),
                        Instant.parse("2026-11-21T00:00:00Z")),
                new SalesIndicatorsCalculator.OpportunityMetric(
                        OpportunityStatus.LOST, new BigDecimal("500.00"), 0,
                        Instant.parse("2026-11-01T00:00:00Z"), null),
                new SalesIndicatorsCalculator.OpportunityMetric(
                        OpportunityStatus.OPEN, new BigDecimal("1200.00"), 25,
                        Instant.parse("2026-11-05T00:00:00Z"), null)));

        assertThat(indicators.wonCount()).isEqualTo(2);
        assertThat(indicators.lostCount()).isEqualTo(1);
        assertThat(indicators.conversionRate()).isEqualByComparingTo("66.67");
        assertThat(indicators.averageWonTicket()).isEqualByComparingTo("1500.00");
        assertThat(indicators.averageSalesCycleDays()).isEqualByComparingTo("15.00");
        assertThat(indicators.weightedOpenPipeline()).isEqualByComparingTo("300.00");
        assertThat(indicators.annualizedWonValue()).isEqualByComparingTo("36000.00");
    }

    @Test
    void zeroDenominatorsRemainZeroInsteadOfDividing() {
        var indicators = SalesIndicatorsCalculator.calculate(List.of());

        assertThat(indicators.conversionRate()).isEqualByComparingTo("0.00");
        assertThat(indicators.averageWonTicket()).isEqualByComparingTo("0.00");
        assertThat(indicators.averageSalesCycleDays()).isEqualByComparingTo("0.00");
    }
}
