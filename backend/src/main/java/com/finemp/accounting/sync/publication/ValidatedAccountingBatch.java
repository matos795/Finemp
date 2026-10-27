package com.finemp.accounting.sync.publication;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import java.util.*;

public record ValidatedAccountingBatch(long readCount, List<ImportedAccountingMovement> uniqueMovements,
                                       Map<String, AccountDescriptor> accounts) {
    public ValidatedAccountingBatch {
        uniqueMovements = List.copyOf(uniqueMovements);
        accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
    }
    public record MovementIdentity(String sourceEntryKey, AccountingMovementSide side) { }
    public record AccountDescriptor(String externalId, String accountKey, String description) { }
    @Override public String toString() {
        return "ValidatedAccountingBatch[readCount=" + readCount + ", uniqueCount=" + uniqueMovements.size() + "]";
    }
}

