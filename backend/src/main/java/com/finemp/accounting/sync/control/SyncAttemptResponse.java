package com.finemp.accounting.sync.control;

import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.*;
import java.time.Instant;
import java.util.UUID;

public record SyncAttemptResponse(UUID id, int attemptNumber, SyncAttemptStatus status, Instant startedAt,
        Instant lastRenewedAt, Instant leaseExpiresAt, Instant finishedAt, Instant nextAttemptAt, SyncFailureCode failureCode) {
    public static SyncAttemptResponse from(SyncAttempt attempt) {
        return new SyncAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getLastRenewedAt(), attempt.getLeaseExpiresAt(), attempt.getFinishedAt(), attempt.getNextAttemptAt(), attempt.getFailureCode());
    }
}
