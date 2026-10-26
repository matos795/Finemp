package com.finemp.identity;

/** Secrets are validated by the service, never put into Bean Validation rejected-value messages. */
public record CompleteAccountActionRequest(
        String token,
        String newPassword) {
    @Override
    public String toString() { return "CompleteAccountActionRequest[token=redacted, newPassword=redacted]"; }
}
