package com.finemp.planning.goal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalMeasurementResponse(
        UUID id,
        LocalDate measuredOn,
        BigDecimal measuredValue,
        String notes,
        Instant createdAt) {

    public static GoalMeasurementResponse from(GoalMeasurement measurement) {
        return new GoalMeasurementResponse(
                measurement.getId(),
                measurement.getMeasuredOn(),
                measurement.getMeasuredValue(),
                measurement.getNotes(),
                measurement.getCreatedAt());
    }
}
