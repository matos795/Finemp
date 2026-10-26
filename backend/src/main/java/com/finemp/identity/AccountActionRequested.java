package com.finemp.identity;

import java.time.Instant;

/** Transient delivery event. Its raw token must never be persisted or logged. */
public record AccountActionRequested(
        String email,
        AccountActionPurpose purpose,
        OpaqueAccountToken token,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "AccountActionRequested[purpose=" + purpose + ", token=redacted]";
    }
}
