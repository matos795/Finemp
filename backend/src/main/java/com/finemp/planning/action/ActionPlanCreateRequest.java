package com.finemp.planning.action;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ActionPlanCreateRequest(
        UUID goalId,
        @NotBlank @Size(max = 240) String what,
        @Size(max = 5000) String why,
        @NotBlank @Size(max = 160) String responsible,
        LocalDate dueOn,
        @Size(max = 200) String where,
        @Size(max = 5000) String how,
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal cost,
        @NotBlank @Size(max = 40) String workflowStatus) {
}
