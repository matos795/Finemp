package com.finemp.connector;

import java.time.Instant;
import java.util.UUID;

public record ConnectorCredentialResponse(UUID id, UUID accountingFirmId, UUID accountingSourceId, String label,
        Instant createdAt, Instant expiresAt, Instant revokedAt, long version) {
    public static ConnectorCredentialResponse from(ConnectorCredential credential) {
        return new ConnectorCredentialResponse(credential.getId(), credential.getAccountingFirmId(), credential.getAccountingSourceId(),
                credential.getLabel(), credential.getCreatedAt(), credential.getExpiresAt(), credential.getRevokedAt(), credential.getVersion());
    }
}
