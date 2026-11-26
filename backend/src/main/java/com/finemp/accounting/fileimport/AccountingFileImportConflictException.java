package com.finemp.accounting.fileimport;

public final class AccountingFileImportConflictException extends RuntimeException {
    public enum Reason {
        IDEMPOTENCY_MISMATCH,
        SOURCE_NOT_AVAILABLE,
        PERIOD_OVERLAP,
        PUBLISHED_DATA_CONFLICT
    }

    private final Reason reason;

    public AccountingFileImportConflictException(Reason reason) {
        super("Accounting file import conflicts with the current state.");
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
