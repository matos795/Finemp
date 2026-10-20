package com.finemp.accounting.classification;

import java.util.UUID;

public record AccountingAccountClassificationRow(
        UUID accountId,
        UUID accountingSourceId,
        String accountKey,
        String description,
        UUID classificationId,
        String dreClassification,
        String dreOpenClassification,
        String ebitdaClassification) {
}