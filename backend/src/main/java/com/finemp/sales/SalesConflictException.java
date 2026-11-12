package com.finemp.sales;

public class SalesConflictException extends RuntimeException {

    public enum Reason {
        STALE_VERSION,
        DUPLICATE_STAGE
    }

    private final Reason reason;

    public SalesConflictException(Reason reason) {
        super("Conflito comercial: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
