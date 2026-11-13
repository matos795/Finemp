package com.finemp.sales;

import jakarta.validation.constraints.*;

public record OpportunityStageRequest(
        @NotBlank @Size(max = 80) String stage,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
