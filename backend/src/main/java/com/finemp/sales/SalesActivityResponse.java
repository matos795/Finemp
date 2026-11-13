package com.finemp.sales;

import java.time.*;
import java.util.UUID;

public record SalesActivityResponse(
        UUID id,
        UUID companyId,
        UUID opportunityId,
        String opportunityTitle,
        UUID businessPartnerId,
        String businessPartnerName,
        String subject,
        String responsible,
        LocalDate dueOn,
        String notes,
        Instant completedAt,
        SalesActivityDueStatus dueStatus,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static SalesActivityResponse from(SalesActivity activity, LocalDate today) {
        var opportunity = activity.getOpportunity();
        var partner = activity.getBusinessPartner();
        return new SalesActivityResponse(
                activity.getId(),
                activity.getCompany().getId(),
                opportunity == null ? null : opportunity.getId(),
                opportunity == null ? null : opportunity.getValues().title(),
                partner == null ? null : partner.getId(),
                partner == null ? null : partner.getProfile().name(),
                activity.getSubject(),
                activity.getResponsible(),
                activity.getDueOn(),
                activity.getNotes(),
                activity.getCompletedAt(),
                activity.dueStatus(today),
                activity.getVersion(),
                activity.getCreatedAt(),
                activity.getUpdatedAt());
    }
}
