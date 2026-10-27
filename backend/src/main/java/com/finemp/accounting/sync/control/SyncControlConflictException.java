package com.finemp.accounting.sync.control;

public class SyncControlConflictException extends RuntimeException {
    public enum Reason { IDEMPOTENCY_MISMATCH, ACTIVE_RUN_EXISTS, SOURCE_NOT_AVAILABLE, RUN_NOT_CANCELABLE }
    private final Reason reason;
    public SyncControlConflictException(Reason reason) { super("Operação incompatível com o estado da sincronização."); this.reason = reason; }
    public Reason getReason() { return reason; }
}

