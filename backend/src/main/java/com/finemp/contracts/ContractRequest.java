package com.finemp.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record ContractRequest(
        @NotNull UUID businessPartnerId,
        @NotNull ContractDirection direction,
        @NotBlank @Size(max = 255) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        int billingDay,
        @NotBlank String monthlyAmount,
        boolean active,
        Long expectedVersion) {
}
