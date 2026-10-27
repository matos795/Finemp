package com.finemp.connector;

public record ConnectorSecretResponse(ConnectorCredentialResponse credential, String secret) {
    @Override public String toString() { return "ConnectorSecretResponse[secret=redacted]"; }
}
