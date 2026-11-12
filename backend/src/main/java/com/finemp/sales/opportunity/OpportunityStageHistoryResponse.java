package com.finemp.sales.opportunity;

import java.time.Instant;
import java.util.UUID;

public record OpportunityStageHistoryResponse(
        UUID id,
        UUID stageId,
        String stageName,
        int stagePosition,
        UUID actorUserAccountId,
        Instant occurredAt) {

    public static OpportunityStageHistoryResponse from(
            OpportunityStageHistory history) {

        return new OpportunityStageHistoryResponse(
                history.getId(),
                history.getStage().getId(),
                history.getStageName(),
                history.getStagePosition(),
                history.getActorUserAccountId(),
                history.getOccurredAt());
    }
}
