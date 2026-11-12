package com.finemp.sales.opportunity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OpportunityCreateRequest(
        UUID businessPartnerId,
        @NotNull UUID stageId,
        @NotBlank @Size(max = 180) String title,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        boolean monthlyRecurring,
        @Min(0) @Max(100) int probability,
        @NotBlank @Size(max = 160) String responsible,
        @NotNull LocalDate expectedCloseOn,
        @Size(max = 5000) String notes) {
}
