package com.finemp.planning.goal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalMeasurementRequest(
        @NotNull LocalDate measuredOn,
        @NotNull BigDecimal measuredValue,
        @Size(max = 5000) String notes) {
}
