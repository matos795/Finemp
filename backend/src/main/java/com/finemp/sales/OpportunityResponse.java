package com.finemp.sales;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record OpportunityResponse(
        UUID id,
        UUID companyId,
        UUID businessPartnerId,
        String businessPartnerName,
        String title,
        String stage,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthlyAmount,
        int probability,
        String responsible,
        LocalDate expectedCloseOn,
        String notes,
        OpportunityStatus status,
        Instant wonAt,
        Instant lostAt,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<StageHistoryResponse> stageHistory) {

    static OpportunityResponse from(Opportunity opportunity) {
        var partner = opportunity.getBusinessPartner();
        var values = opportunity.getValues();
        return new OpportunityResponse(
                opportunity.getId(),
                opportunity.getCompany().getId(),
                partner == null ? null : partner.getId(),
                partner == null ? null : partner.getProfile().name(),
                values.title(),
                values.stage(),
                values.monthlyAmount(),
                values.probability(),
                values.responsible(),
                values.expectedCloseOn(),
                values.notes(),
                opportunity.getStatus(),
                opportunity.getWonAt(),
                opportunity.getLostAt(),
                opportunity.getVersion(),
                opportunity.getCreatedAt(),
                opportunity.getUpdatedAt(),
                opportunity.getStageHistory().stream()
                        .map(StageHistoryResponse::from)
                        .toList());
    }

    public record StageHistoryResponse(String stage, Instant recordedAt) {
        static StageHistoryResponse from(OpportunityStageHistory history) {
            return new StageHistoryResponse(history.getStage(), history.getRecordedAt());
        }
    }
}
