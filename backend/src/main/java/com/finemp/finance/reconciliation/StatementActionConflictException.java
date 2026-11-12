package com.finemp.finance.reconciliation;

public class StatementActionConflictException extends RuntimeException {
    public enum Reason { IDEMPOTENCY_MISMATCH,STALE_VERSION,INVALID_STATE,NOT_A_CANDIDATE,TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public StatementActionConflictException(Reason reason){super("Conflito no movimento de extrato.");this.reason=reason;}
    public Reason getReason(){return reason;}
}
