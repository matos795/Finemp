package com.finemp.sales.indicators;

import com.finemp.sales.opportunity.OpportunityOutcome;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OpportunityIndicatorRow(
        BigDecimal amount,
        boolean monthlyRecurring,
        int probability,
        OpportunityOutcome outcome,
        LocalDate openedOn,
        LocalDate closedOn,
        LocalDate expectedCloseOn) {

    public SalesOpportunityMetric toMetric() {
        return new SalesOpportunityMetric(
                amount,
                monthlyRecurring,
                probability,
                outcome,
                openedOn,
                closedOn,
                expectedCloseOn);
    }
}
