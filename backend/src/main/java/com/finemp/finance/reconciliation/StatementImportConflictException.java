package com.finemp.finance.reconciliation;

public class StatementImportConflictException extends RuntimeException {
    public enum Reason { IDEMPOTENCY_MISMATCH,INACTIVE_BANK,DUPLICATE_FITID,TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public StatementImportConflictException(Reason reason){super("Conflito na confirmação de extrato.");this.reason=reason;}
    public Reason getReason(){return reason;}
}
