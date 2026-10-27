package com.finemp.accounting.sync.execution;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import java.util.List;

/** Must atomically publish movements, counters and SUCCEEDED, while checking the source/fence. */
public interface SyncPublicationPort {
    void publish(ClaimedSyncRun claim, List<ImportedAccountingMovement> movements);
}

