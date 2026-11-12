package com.finemp.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        AccessAuditAction action,
        UUID actorUserAccountId,
        UUID subjectUserAccountId,
        UUID accountingFirmId,
        UUID companyId,
        Instant occurredAt,
        String resourceType,
        UUID resourceId,
        UUID commandId,
        String beforeState,
        String afterState,
        String reason) {

    public static AuditEventResponse from(AccessAuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getAction(),
                event.getActorUserAccountId(),
                event.getSubjectUserAccountId(),
                event.getAccountingFirmId(),
                event.getCompanyId(),
                event.getOccurredAt(),
                event.getResourceType(),
                event.getResourceId(),
                event.getCommandId(),
                event.getBeforeState(),
                event.getAfterState(),
                event.getReason());
    }
}
