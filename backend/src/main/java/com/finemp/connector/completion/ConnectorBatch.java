package com.finemp.connector.completion;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import java.util.List;

public record ConnectorBatch(ExternalCompanyIdentity observedIdentity, List<ImportedAccountingMovement> movements) {
    public ConnectorBatch { movements = List.copyOf(movements); }
    @Override public String toString() { return "ConnectorBatch[records=" + movements.size() + "]"; }
}
