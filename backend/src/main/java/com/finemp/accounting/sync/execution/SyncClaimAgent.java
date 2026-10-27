package com.finemp.accounting.sync.execution;

import java.util.UUID;

public record SyncClaimAgent(UUID credentialId, Long credentialVersion) {
    public SyncClaimAgent {
        if ((credentialId == null) != (credentialVersion == null) || (credentialVersion != null && credentialVersion < 0))
            throw new IllegalArgumentException("Invalid sync agent binding.");
    }
    public static SyncClaimAgent direct() { return new SyncClaimAgent(null, null); }
    public boolean isConnector() { return credentialId != null; }
}
