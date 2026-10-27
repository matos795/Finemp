package com.finemp.accounting.sync.execution;

import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.sync.SyncRunMode;
import java.util.Objects;
import java.util.UUID;

public record ClaimedSyncRun(UUID runId, long version, SyncRunMode mode, AccountingReadRequest request) {
    public ClaimedSyncRun {
        Objects.requireNonNull(runId); Objects.requireNonNull(mode); Objects.requireNonNull(request);
        if (version < 0) throw new IllegalArgumentException("Invalid sync fence.");
    }
    @Override public String toString() { return "ClaimedSyncRun[runId=" + runId + ", version=" + version + "]"; }
}

