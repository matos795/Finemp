package com.finemp.sales;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record SalesActivityRequest(
        UUID opportunityId,
        UUID businessPartnerId,
        @NotBlank @Size(max = 180) String subject,
        @NotBlank @Size(max = 160) String responsible,
        @NotNull LocalDate dueOn,
        @Size(max = 4000) String notes,
        Long expectedVersion) {
}
