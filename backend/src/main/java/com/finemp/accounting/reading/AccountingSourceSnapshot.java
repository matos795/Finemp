package com.finemp.accounting.reading;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import java.util.*;

public record AccountingSourceSnapshot(ExternalCompanyIdentity identity, List<ImportedAccountingMovement> movements) {
    public AccountingSourceSnapshot { Objects.requireNonNull(identity); movements = List.copyOf(movements); }
    @Override public String toString() { return "AccountingSourceSnapshot[records=" + movements.size() + "]"; }
}
