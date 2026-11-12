package com.finemp.sales.stage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SalesStageCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) int position) {
}
