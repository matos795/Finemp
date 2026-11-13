package com.finemp.sales;

import jakarta.validation.constraints.*;

public record SalesActivityCompletionRequest(
        boolean completed,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
