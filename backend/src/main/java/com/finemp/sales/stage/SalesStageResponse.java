package com.finemp.sales.stage;

import java.util.UUID;

public record SalesStageResponse(
        UUID id,
        String name,
        int position,
        boolean active,
        long version) {

    public static SalesStageResponse from(SalesStage stage) {
        return new SalesStageResponse(
                stage.getId(),
                stage.getName(),
                stage.getPosition(),
                stage.isActive(),
                stage.getVersion());
    }
}
