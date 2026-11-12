package com.finemp.planning.goal;

import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String name,
        GoalDirection direction,
        String baselineValue,
        String targetValue,
        LocalDate dueOn,
        String notes,
        boolean active,
        long version,
        String currentValue,
        LocalDate currentMeasuredOn,
        String progress,
        Boolean achieved) {

    public static GoalResponse from(
            Goal goal,
            GoalMeasurement latest) {

        if (latest == null) {
            return new GoalResponse(
                    goal.getId(),
                    goal.getName(),
                    goal.getDirection(),
                    goal.getBaselineValue().toPlainString(),
                    goal.getTargetValue().toPlainString(),
                    goal.getDueOn(),
                    goal.getNotes(),
                    goal.isActive(),
                    goal.getVersion(),
                    null,
                    null,
                    null,
                    null);
        }

        var current = latest.getMeasuredValue();

        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getDirection(),
                goal.getBaselineValue(),
                goal.getTargetValue(),
                goal.getDueOn(),
                goal.getNotes(),
                goal.isActive(),
                goal.getVersion(),
                current,
                latest.getMeasuredOn(),
                GoalProgress.calculate(
                        goal.getDirection(),
                        goal.getBaselineValue().toPlainString(),
                        goal.getTargetValue().toPlainString(),
                        current),
                GoalProgress.isAchieved(
                        goal.getDirection(),
                        current,
                        goal.getTargetValue()));
    }
}
