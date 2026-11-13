package com.finemp.sales;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record OpportunityRequest(
        UUID businessPartnerId,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 80) String stage,
        @NotBlank String monthlyAmount,
        @Min(0) @Max(100) int probability,
        @NotBlank @Size(max = 160) String responsible,
        LocalDate expectedCloseOn,
        @Size(max = 4000) String notes,
        Long expectedVersion) {
}
