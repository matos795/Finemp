package com.finemp.accounting.reading;

import com.finemp.accounting.source.AccountingSourceType;

public interface AccountingSourceSnapshotReader {
    AccountingSourceType sourceType();
    AccountingSourceSnapshot readSnapshot(AccountingReadRequest request);
}
