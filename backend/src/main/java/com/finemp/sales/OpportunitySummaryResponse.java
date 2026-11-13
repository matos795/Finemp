package com.finemp.sales;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record OpportunitySummaryResponse(
        UUID id,
        UUID businessPartnerId,
        String businessPartnerName,
        String title,
        String stage,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthlyAmount,
        int probability,
        String responsible,
        LocalDate expectedCloseOn,
        OpportunityStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static OpportunitySummaryResponse from(Opportunity opportunity) {
        var partner = opportunity.getBusinessPartner();
        var values = opportunity.getValues();
        return new OpportunitySummaryResponse(
                opportunity.getId(),
                partner == null ? null : partner.getId(),
                partner == null ? null : partner.getProfile().name(),
                values.title(),
                values.stage(),
                values.monthlyAmount(),
                values.probability(),
                values.responsible(),
                values.expectedCloseOn(),
                opportunity.getStatus(),
                opportunity.getVersion(),
                opportunity.getCreatedAt(),
                opportunity.getUpdatedAt());
    }
}
