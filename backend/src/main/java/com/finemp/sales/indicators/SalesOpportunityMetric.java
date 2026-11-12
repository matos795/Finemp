package com.finemp.sales.indicators;

import com.finemp.sales.opportunity.OpportunityOutcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record SalesOpportunityMetric(
        BigDecimal amount,
        boolean monthlyRecurring,
        int probability,
        OpportunityOutcome outcome,
        LocalDate openedOn,
        LocalDate closedOn,
        LocalDate expectedCloseOn) {

    public SalesOpportunityMetric {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(openedOn);
        Objects.requireNonNull(expectedCloseOn);

        if (amount.signum() < 0 || probability < 0 || probability > 100) {
            throw new IllegalArgumentException("Métrica comercial inválida.");
        }

        if (outcome == OpportunityOutcome.OPEN && closedOn != null) {
            throw new IllegalArgumentException("Oportunidade aberta não possui fechamento.");
        }

        if (outcome != OpportunityOutcome.OPEN && closedOn == null) {
            throw new IllegalArgumentException("Oportunidade encerrada exige data.");
        }
    }

    public BigDecimal annualizedAmount() {
        return monthlyRecurring
                ? amount.multiply(BigDecimal.valueOf(12))
                : amount;
    }
}
