package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.AccountingMovementIngestionService;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        prefix = "finemp.integration.questor",
        name = "enabled",
        havingValue = "true"
)
public class QuestorAccountingImportService {

    private final AccountingSourceRepository accountingSourceRepository;
    private final QuestorAccountingReader questorAccountingReader;
    private final AccountingMovementIngestionService ingestionService;

    public QuestorAccountingImportService(
            AccountingSourceRepository accountingSourceRepository,
            QuestorAccountingReader questorAccountingReader,
            AccountingMovementIngestionService ingestionService) {

        this.accountingSourceRepository = accountingSourceRepository;
        this.questorAccountingReader = questorAccountingReader;
        this.ingestionService = ingestionService;
    }

    public void importMovements(
            UUID accountingSourceId,
            LocalDate startDate,
            LocalDate endDate) {

        AccountingSource accountingSource = accountingSourceRepository
                .findById(accountingSourceId)
                .orElseThrow();

        List<ImportedAccountingMovement> movements = questorAccountingReader.readMovements(
                accountingSource.getExternalReference(),
                startDate,
                endDate);

        ingestionService.ingest(accountingSourceId, movements);
    }
}