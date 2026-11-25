package com.finemp.people;

import java.util.UUID;

public record JobPositionResponse(
        UUID id,
        String title,
        String description,
        boolean active,
        long version) {

    static JobPositionResponse from(JobPosition position) {
        return new JobPositionResponse(
                position.getId(),
                position.getTitle(),
                position.getDescription(),
                position.isActive(),
                position.getVersion());
    }
}
