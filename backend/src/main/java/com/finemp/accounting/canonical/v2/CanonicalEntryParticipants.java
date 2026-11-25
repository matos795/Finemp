package com.finemp.accounting.canonical.v2;

public record CanonicalEntryParticipants(
        String sourceEntryKey,
        String debitParticipantCode,
        String creditParticipantCode) {
}
