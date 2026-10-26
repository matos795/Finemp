package com.finemp.identity.delivery;

import com.finemp.identity.AccountActionPurpose;
import java.net.URI;
import java.time.Instant;

public record AccountActionMail(
        String recipient,
        URI actionLink,
        AccountActionPurpose purpose,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "AccountActionMail[purpose=" + purpose + ", link=redacted]";
    }
}
