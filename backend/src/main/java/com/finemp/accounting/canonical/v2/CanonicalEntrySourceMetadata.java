package com.finemp.accounting.canonical.v2;

import java.time.Instant;

public record CanonicalEntrySourceMetadata(
        String sourceEntryKey,
        Instant sourceRecordedAt,
        String historyCode,
        String originCode,
        String entryTypeCode,
        String dataOriginCode,
        String batchCode,
        String documentNumber,
        String operationGroupKey,
        String transactionKey,
        String originEstablishmentCode) {
}
