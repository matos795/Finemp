package com.finemp.sales.opportunity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record OpportunityMoveStageRequest(
        @NotNull UUID stageId,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
