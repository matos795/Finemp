package com.finemp.accounting.classification.importing;

import com.finemp.accounting.classification.DreClassificationKey;

import java.util.List;

public record ClassificationImportPreviewResponse(
        int total,
        int ready,
        int alreadyClassified,
        int mappingNotFound,
        int classificationNotFound,
        int conflicts,
        int catalogAdditions,
        List<ClassificationImportPreviewItem> items) {

    public static ClassificationImportPreviewResponse from(
            List<ClassificationImportPreviewItem> items) {

        int catalogAdditions = (int) items.stream()
                .filter(item -> item.status() == ClassificationImportStatus.CLASSIFICATION_NOT_FOUND)
                .map(item -> new DreClassificationKey(
                        item.dreClassification(),
                        item.dreOpenClassification(),
                        item.ebitdaClassification()))
                .distinct()
                .count();

        return new ClassificationImportPreviewResponse(
                items.size(),
                count(items, ClassificationImportStatus.READY),
                count(items, ClassificationImportStatus.ALREADY_CLASSIFIED),
                count(items, ClassificationImportStatus.MAPPING_NOT_FOUND),
                count(items, ClassificationImportStatus.CLASSIFICATION_NOT_FOUND),
                count(items, ClassificationImportStatus.CONFLICT),
                catalogAdditions,
                List.copyOf(items));
    }

    private static int count(
            List<ClassificationImportPreviewItem> items,
            ClassificationImportStatus status) {

        return (int) items.stream()
                .filter(item -> item.status() == status)
                .count();
    }
}