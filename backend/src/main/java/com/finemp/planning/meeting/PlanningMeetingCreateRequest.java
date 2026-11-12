package com.finemp.planning.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PlanningMeetingCreateRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 80) String stage,
        @NotNull LocalDate scheduledOn,
        @Size(max = 5000) String notes,
        @Size(max = 10000) String diagnosticText) {
}
