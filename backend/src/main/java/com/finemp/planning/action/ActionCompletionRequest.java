package com.finemp.planning.action;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ActionCompletionRequest(
        boolean completed,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
