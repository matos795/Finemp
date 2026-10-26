package com.finemp.accounting.classification;

public record DreClassificationKey(
        String dreClassification,
        String dreOpenClassification,
        String ebitdaClassification) {

    public static DreClassificationKey from(
            DreClassification classification) {

        return new DreClassificationKey(
                classification.getDreClassification(),
                classification.getDreOpenClassification(),
                classification.getEbitdaClassification());
    }
}