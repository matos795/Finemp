package com.finemp.planning.goal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalCreateRequest(
        @NotBlank @Size(max = 180) String name,
        @NotNull GoalDirection direction,
        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal baselineValue,
        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal targetValue,
        @NotNull LocalDate dueOn,
        @Size(max = 5000) String notes) {
}
