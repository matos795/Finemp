package com.finemp.sales.activity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SalesActivityCommandRequest(
        @NotNull SalesActivityCommand command,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
