package com.finemp.accounting.source;

import java.util.UUID;

public record ReadableAccountingSourceResponse(
        UUID id,
        AccountingSourceType type,
        String externalReference,
        boolean active,
        AccountingConnectionMode connectionMode) {

    public static ReadableAccountingSourceResponse from(AccountingSource source) {
        return new ReadableAccountingSourceResponse(
                source.getId(),
                source.getType(),
                source.getExternalReference(),
                source.isActive(),
                source.getConnectionMode());
    }
}
