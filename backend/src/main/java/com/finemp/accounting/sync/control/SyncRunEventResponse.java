package com.finemp.accounting.sync.control;

import com.finemp.accounting.sync.*;
import java.time.Instant;
import java.util.UUID;

public record SyncRunEventResponse(UUID id, SyncRunStatus status, SyncRunActorType actorType, UUID actorId, Instant occurredAt, int transitionIndex) {
    public static SyncRunEventResponse from(SyncRunEvent event) {
        return new SyncRunEventResponse(event.getId(), event.getStatus(), event.getActorType(), event.getActorId(), event.getOccurredAt(), event.getTransitionIndex());
    }
}

