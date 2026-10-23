package com.finemp.accounting.classification.importing;

import java.util.UUID;

public record ClassificationImportPreviewItem(
                UUID accountId,
                String accountKey,
                String accountDescription,
                ClassificationImportStatus status,
                UUID currentClassificationId,
                UUID proposedClassificationId,
                String dreClassification,
                String dreOpenClassification,
                String ebitdaClassification) {
}