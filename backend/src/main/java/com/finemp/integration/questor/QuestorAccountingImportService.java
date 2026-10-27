package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.AccountingMovementIngestionService;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.reading.AccountingSourceReaderRegistry;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.administration.SaasResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnExpression("${finemp.integration.questor.enabled:false} && !${finemp.sync.executor.enabled:false}")
public class QuestorAccountingImportService {

    private final AccountingSourceRepository accountingSourceRepository;
    private final AccountingSourceReaderRegistry readers;
    private final AccountingMovementIngestionService ingestionService;

    public QuestorAccountingImportService(
            AccountingSourceRepository accountingSourceRepository,
            AccountingSourceReaderRegistry readers,
            AccountingMovementIngestionService ingestionService) {

        this.accountingSourceRepository = accountingSourceRepository;
        this.readers = readers;
        this.ingestionService = ingestionService;
    }

    public void importMovements(
            UUID accountingSourceId,
            LocalDate startDate,
            LocalDate endDate) {

        AccountingSource accountingSource = accountingSourceRepository
                .findForReadingById(accountingSourceId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Accounting source"));

        if (!accountingSource.isActive() || accountingSource.getType() != AccountingSourceType.QUESTOR)
            throw new QuestorSourceStateException();

        var request = new AccountingReadRequest(accountingSource.getId(), accountingSource.getCompany().getId(),
                accountingSource.getType(), accountingSource.getExternalReference(), accountingSource.getCompany().getCnpj(),
                startDate, endDate);
        List<ImportedAccountingMovement> movements = readers.readMovements(request);

        ingestionService.ingest(accountingSourceId, movements);
    }
}
