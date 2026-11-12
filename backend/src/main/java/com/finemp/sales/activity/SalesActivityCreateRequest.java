package com.finemp.sales.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record SalesActivityCreateRequest(
        UUID opportunityId,
        UUID businessPartnerId,
        @NotBlank @Size(max = 180) String title,
        @Size(max = 5000) String notes,
        @NotBlank @Size(max = 160) String responsible,
        @NotNull LocalDate dueOn) {
}
