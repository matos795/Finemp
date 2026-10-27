package com.finemp.accounting.reading;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.source.AccountingSourceType;
import java.util.List;

public interface AccountingSourceReader {
    AccountingSourceType sourceType();
    List<ImportedAccountingMovement> readMovements(AccountingReadRequest request);
}
