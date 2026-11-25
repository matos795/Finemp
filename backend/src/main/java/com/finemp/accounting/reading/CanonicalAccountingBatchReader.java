package com.finemp.accounting.reading;

import com.finemp.accounting.canonical.v2.CanonicalAccountingBatch;
import com.finemp.accounting.source.AccountingSourceType;

/**
 * Reads one canonical v2 accounting batch from an external source.
 * Source-specific invariants remain at the adapter boundary.
 */
public interface CanonicalAccountingBatchReader {
    AccountingSourceType sourceType();
    CanonicalAccountingBatch readBatch(AccountingReadRequest request);
}
