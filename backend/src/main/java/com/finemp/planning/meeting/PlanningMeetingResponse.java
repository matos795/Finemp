package com.finemp.planning.meeting;

import java.time.LocalDate;
import java.util.UUID;

public record PlanningMeetingResponse(
        UUID id,
        String title,
        String stage,
        LocalDate scheduledOn,
        String notes,
        String diagnosticText,
        long version) {

    public static PlanningMeetingResponse from(PlanningMeeting meeting) {
        return new PlanningMeetingResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getStage(),
                meeting.getScheduledOn(),
                meeting.getNotes(),
                meeting.getDiagnosticText(),
                meeting.getVersion());
    }
}
