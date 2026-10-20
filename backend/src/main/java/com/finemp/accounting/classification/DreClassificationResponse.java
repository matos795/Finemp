package com.finemp.accounting.classification;

import java.util.UUID;

public record DreClassificationResponse(
        UUID id,
        String dreClassification,
        String dreOpenClassification,
        String ebitdaClassification) {

    public static DreClassificationResponse from(
            DreClassification classification) {

        return new DreClassificationResponse(
                classification.getId(),
                classification.getDreClassification(),
                classification.getDreOpenClassification(),
                classification.getEbitdaClassification());
    }
}