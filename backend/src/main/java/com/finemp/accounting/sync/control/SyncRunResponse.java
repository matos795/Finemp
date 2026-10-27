package com.finemp.accounting.sync.control;

import com.finemp.accounting.sync.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SyncRunResponse(UUID id, UUID accountingSourceId, UUID requestedByUserId, SyncRunMode mode,
        SyncRunStatus status, LocalDate startDate, LocalDate endDate, Instant requestedAt, Instant startedAt,
        Instant finishedAt, Long readCount, Long importedCount, Long skippedCount, SyncFailureCode failureCode) {
    public static SyncRunResponse from(SyncRun run) {
        return new SyncRunResponse(run.getId(), run.getAccountingSource().getId(), run.getRequestedByUserId(),
                run.getMode(), run.getStatus(), run.getStartDate(), run.getEndDate(), run.getRequestedAt(),
                run.getStartedAt(), run.getFinishedAt(), run.getReadCount(), run.getImportedCount(), run.getSkippedCount(), run.getFailureCode());
    }
}

