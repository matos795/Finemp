package com.finemp.sales.opportunity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OpportunityOutcomeRequest(
        @NotNull OpportunityOutcome outcome,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
