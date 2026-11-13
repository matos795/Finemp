package com.finemp.sales;

import jakarta.validation.constraints.*;

public record OpportunityStatusRequest(
        @NotNull OpportunityStatus status,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
