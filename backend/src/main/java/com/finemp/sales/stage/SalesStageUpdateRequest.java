package com.finemp.sales.stage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SalesStageUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) int position,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
