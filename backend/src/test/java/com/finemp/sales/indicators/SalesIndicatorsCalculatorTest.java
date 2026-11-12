package com.finemp.sales.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.sales.opportunity.OpportunityOutcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesIndicatorsCalculatorTest {

    @Test
    void annualizesOnlyMonthlyRecurringValuesAndWeightsOpenPipeline() {
        var result = SalesIndicatorsCalculator.calculate(
                List.of(
                        metric("100.00", true, 50, OpportunityOutcome.OPEN,
                                "2026-11-01", null, "2026-10-10"),
                        metric("1200.00", false, 25, OpportunityOutcome.OPEN,
                                "2026-11-01", null, "2026-12-15")),
                LocalDate.of(2026, 11, 12));

        assertThat(result.openPipelineNominal())
                .isEqualByComparingTo("1300.00");
        assertThat(result.openPipelineWeighted())
                .isEqualByComparingTo("350.00");
        assertThat(result.openPipelineAnnualized())
                .isEqualByComparingTo("2400.00");
        assertThat(result.openPipelineWeightedAnnualized())
                .isEqualByComparingTo("900.00");

        assertThat(result.forecast()).hasSize(2);
        assertThat(result.forecast().get(0).month()).isEqualTo("2026-11");
        assertThat(result.forecast().get(0).weightedAnnualized())
                .isEqualByComparingTo("600.00");
        assertThat(result.forecast().get(1).month()).isEqualTo("2026-12");
        assertThat(result.forecast().get(1).weightedAnnualized())
                .isEqualByComparingTo("300.00");
    }

    @Test
    void calculatesTicketConversionAndAverageCommercialCycleFromCurrentOutcomes() {
        var result = SalesIndicatorsCalculator.calculate(
                List.of(
                        metric("100.00", false, 100, OpportunityOutcome.WON,
                                "2026-01-01", "2026-01-11", "2026-01-10"),
                        metric("300.00", false, 100, OpportunityOutcome.WON,
                                "2026-02-01", "2026-02-21", "2026-02-20"),
                        metric("999.00", false, 100, OpportunityOutcome.LOST,
                                "2026-03-01", "2026-03-05", "2026-03-05"),
                        metric("500.00", false, 50, OpportunityOutcome.OPEN,
                                "2026-04-01", null, "2026-04-30")),
                LocalDate.of(2026, 11, 12));

        assertThat(result.wonCount()).isEqualTo(2);
        assertThat(result.lostCount()).isEqualTo(1);
        assertThat(result.wonNominalValue())
                .isEqualByComparingTo("400.00");
        assertThat(result.averageTicket())
                .isEqualByComparingTo("200.00");
        assertThat(result.conversionRate())
                .isEqualByComparingTo("0.6667");
        assertThat(result.averageCycleDays())
                .isEqualByComparingTo("15.0000");
    }

    @Test
    void keepsUndefinedRatesNullWhenThereIsNoDenominator() {
        var result = SalesIndicatorsCalculator.calculate(
                List.of(metric("100.00", false, 50, OpportunityOutcome.OPEN,
                        "2026-01-01", null, "2026-02-01")),
                LocalDate.of(2026, 1, 10));

        assertThat(result.averageTicket()).isNull();
        assertThat(result.conversionRate()).isNull();
        assertThat(result.averageCycleDays()).isNull();
    }

    private static SalesOpportunityMetric metric(
            String amount,
            boolean monthlyRecurring,
            int probability,
            OpportunityOutcome outcome,
            String openedOn,
            String closedOn,
            String expectedCloseOn) {

        return new SalesOpportunityMetric(
                new BigDecimal(amount),
                monthlyRecurring,
                probability,
                outcome,
                LocalDate.parse(openedOn),
                closedOn == null ? null : LocalDate.parse(closedOn),
                LocalDate.parse(expectedCloseOn));
    }
}
