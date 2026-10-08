package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;

import java.time.LocalDate;
import java.util.List;

public interface QuestorAccountingReader {

    List<ImportedAccountingMovement> readMovements(
            String externalCompanyReference,
            LocalDate startDate,
            LocalDate endDate
    );
}