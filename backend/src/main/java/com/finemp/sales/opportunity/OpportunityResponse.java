package com.finemp.sales.opportunity;

import com.finemp.finance.partner.BusinessPartner;
import java.time.LocalDate;
import java.util.UUID;

public record OpportunityResponse(
        UUID id,
        UUID businessPartnerId,
        String businessPartnerName,
        UUID stageId,
        String stageName,
        int stagePosition,
        String highestStageName,
        Integer highestStagePosition,
        String title,
        String amount,
        boolean monthlyRecurring,
        int probability,
        String responsible,
        LocalDate expectedCloseOn,
        OpportunityOutcome outcome,
        LocalDate openedOn,
        LocalDate closedOn,
        String notes,
        long version) {

    public static OpportunityResponse from(
            Opportunity opportunity,
            OpportunityStageHistory highestStage) {

        var partner = opportunity.getBusinessPartner();

        return new OpportunityResponse(
                opportunity.getId(),
                partner == null ? null : partner.getId(),
                partner == null ? null : displayName(partner),
                opportunity.getStage().getId(),
                opportunity.getStage().getName(),
                opportunity.getStage().getPosition(),
                highestStage == null ? null : highestStage.getStageName(),
                highestStage == null ? null : highestStage.getStagePosition(),
                opportunity.getTitle(),
                opportunity.getAmount().toPlainString(),
                opportunity.isMonthlyRecurring(),
                opportunity.getProbability(),
                opportunity.getResponsible(),
                opportunity.getExpectedCloseOn(),
                opportunity.getOutcome(),
                opportunity.getOpenedOn(),
                opportunity.getClosedOn(),
                opportunity.getNotes(),
                opportunity.getVersion());
    }

    private static String displayName(BusinessPartner partner) {
        var profile = partner.getProfile();
        return profile.tradeName().isBlank()
                ? profile.name()
                : profile.tradeName();
    }
}
