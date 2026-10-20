package com.finemp.accounting.classification;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AccountingAccountClassificationRequest(
        @NotNull UUID classificationId) {
}