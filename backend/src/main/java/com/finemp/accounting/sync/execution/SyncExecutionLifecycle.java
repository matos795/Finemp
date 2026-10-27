package com.finemp.accounting.sync.execution;

import com.finemp.accounting.sync.SyncFailureCode;
import java.util.Optional;
import java.util.UUID;

public interface SyncExecutionLifecycle {
    Optional<ClaimedSyncRun> claim(UUID runId);
    SyncFailureDisposition failIfCurrent(ClaimedSyncRun claim, SyncFailureCode code);
    boolean renewLease(ClaimedSyncRun claim);
}

