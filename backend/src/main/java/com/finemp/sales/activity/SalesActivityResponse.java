package com.finemp.sales.activity;

import com.finemp.finance.partner.BusinessPartner;
import java.time.LocalDate;
import java.util.UUID;

public record SalesActivityResponse(
        UUID id,
        UUID opportunityId,
        String opportunityTitle,
        UUID businessPartnerId,
        String businessPartnerName,
        String title,
        String notes,
        String responsible,
        LocalDate dueOn,
        SalesActivityState state,
        long version) {

    public static SalesActivityResponse from(
            SalesActivity activity,
            LocalDate today) {

        var opportunity = activity.getOpportunity();
        var partner = activity.getBusinessPartner();

        return new SalesActivityResponse(
                activity.getId(),
                opportunity == null ? null : opportunity.getId(),
                opportunity == null ? null : opportunity.getTitle(),
                partner == null ? null : partner.getId(),
                partner == null ? null : displayName(partner),
                activity.getTitle(),
                activity.getNotes(),
                activity.getResponsible(),
                activity.getDueOn(),
                state(activity, today),
                activity.getVersion());
    }

    static SalesActivityState state(
            SalesActivity activity,
            LocalDate today) {

        if (activity.getCancelledAt() != null) {
            return SalesActivityState.CANCELLED;
        }
        if (activity.getCompletedAt() != null) {
            return SalesActivityState.COMPLETED;
        }
        if (activity.getDueOn().isBefore(today)) {
            return SalesActivityState.OVERDUE;
        }
        if (activity.getDueOn().equals(today)) {
            return SalesActivityState.TODAY;
        }
        return SalesActivityState.PENDING;
    }

    private static String displayName(BusinessPartner partner) {
        var profile = partner.getProfile();
        return profile.tradeName().isBlank()
                ? profile.name()
                : profile.tradeName();
    }
}
