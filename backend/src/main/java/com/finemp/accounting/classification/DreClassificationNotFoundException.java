package com.finemp.accounting.classification;

import java.util.UUID;

public class DreClassificationNotFoundException extends RuntimeException {

    public DreClassificationNotFoundException(UUID classificationId) {
        super("DRE classification not found: " + classificationId);
    }
}