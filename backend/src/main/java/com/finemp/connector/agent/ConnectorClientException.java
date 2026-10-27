package com.finemp.connector.agent;

/** Diagnostics contain only a fixed category/status, never URL, headers, payload or driver causes. */
public final class ConnectorClientException extends RuntimeException {
    public enum Kind { CONFIGURATION, CREDENTIAL, TRANSPORT, TIMEOUT, INTERRUPTED, RESOURCE_LIMIT, PROTOCOL, HTTP }
    private final Kind kind;
    private final int status;
    private final int retryAfterSeconds;
    public ConnectorClientException(Kind kind) { this(kind, 0, 0); }
    public ConnectorClientException(Kind kind, int status, int retryAfterSeconds) {
        super("Connector client: " + kind + (status == 0 ? "" : " (HTTP " + status + ")"), null, false, true);
        this.kind = kind; this.status = status; this.retryAfterSeconds = retryAfterSeconds;
    }
    public Kind kind() { return kind; }
    public int status() { return status; }
    public int retryAfterSeconds() { return retryAfterSeconds; }
}
