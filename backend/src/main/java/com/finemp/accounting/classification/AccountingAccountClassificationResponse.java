package com.finemp.accounting.classification;

import java.util.UUID;

public record AccountingAccountClassificationResponse(
        UUID accountId,
        UUID accountingSourceId,
        String accountKey,
        String description,
        String status,
        UUID classificationId,
        String dreClassification,
        String dreOpenClassification,
        String ebitdaClassification) {

    public static AccountingAccountClassificationResponse from(
            AccountingAccountClassificationRow row) {

        String status = row.classificationId() == null
                ? "PENDING"
                : "CLASSIFIED";

        return new AccountingAccountClassificationResponse(
                row.accountId(),
                row.accountingSourceId(),
                row.accountKey(),
                row.description(),
                status,
                row.classificationId(),
                row.dreClassification(),
                row.dreOpenClassification(),
                row.ebitdaClassification());
    }
}