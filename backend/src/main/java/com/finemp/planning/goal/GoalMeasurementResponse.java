package com.finemp.planning.goal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalMeasurementResponse(
        UUID id,
        LocalDate measuredOn,
        String measuredValue,
        String notes,
        Instant createdAt) {

    public static GoalMeasurementResponse from(GoalMeasurement measurement) {
        return new GoalMeasurementResponse(
                measurement.getId(),
                measurement.getMeasuredOn(),
                measurement.getMeasuredValue().toPlainString(),
                measurement.getNotes(),
                measurement.getCreatedAt());
    }
}
