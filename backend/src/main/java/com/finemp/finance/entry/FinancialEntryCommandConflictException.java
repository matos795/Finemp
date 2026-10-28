package com.finemp.finance.entry;

public class FinancialEntryCommandConflictException extends RuntimeException {
    public enum Reason { STALE_VERSION, IDEMPOTENCY_MISMATCH, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public FinancialEntryCommandConflictException(Reason reason) {super("Conflito no comando financeiro: "+reason);this.reason=reason;}
    public Reason getReason() {return reason;}
}
