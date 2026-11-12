package com.finemp.planning.action;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ActionPlanResponse(
        UUID id,
        UUID goalId,
        String what,
        String why,
        String responsible,
        LocalDate dueOn,
        String where,
        String how,
        BigDecimal cost,
        String workflowStatus,
        Instant completedAt,
        long version) {

    public static ActionPlanResponse from(ActionPlanItem item) {
        return new ActionPlanResponse(
                item.getId(),
                item.getGoal() == null ? null : item.getGoal().getId(),
                item.getWhat(),
                item.getWhy(),
                item.getResponsible(),
                item.getDueOn(),
                item.getWhere(),
                item.getHow(),
                item.getCost(),
                item.getWorkflowStatus(),
                item.getCompletedAt(),
                item.getVersion());
    }
}
