package com.finemp.connector.completion;

import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import java.time.Instant;
import java.util.UUID;

public record ConnectorWorkResult(UUID attemptId, UUID runId, int attemptNumber, boolean currentAttempt,
        SyncAttemptStatus attemptStatus, SyncRunStatus runStatus, Instant leaseExpiresAt, Instant nextAttemptAt,
        SyncFailureCode attemptFailureCode, SyncFailureCode runFailureCode, Long readCount, Long importedCount, Long skippedCount) { }
