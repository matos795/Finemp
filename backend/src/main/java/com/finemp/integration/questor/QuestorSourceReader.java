package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.reading.AccountingSourceReader;
import com.finemp.accounting.source.AccountingSourceType;
import java.util.List;

/** Preserves the proven Questor SQL contract; identity verification is a separate gate. */
public class QuestorSourceReader implements AccountingSourceReader {
    private final QuestorAccountingReader delegate;
    public QuestorSourceReader(QuestorAccountingReader delegate) { this.delegate = delegate; }
    @Override public AccountingSourceType sourceType() { return AccountingSourceType.QUESTOR; }
    @Override public List<ImportedAccountingMovement> readMovements(AccountingReadRequest request) {
        if (request.sourceType() != sourceType()) throw new IllegalArgumentException("Tipo de fonte incompatível.");
        return delegate.readMovements(request.externalCompanyReference(), request.startDate(), request.endDate());
    }
}
