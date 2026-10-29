package com.finemp.finance.withholding;

public class WithholdingConflictException extends RuntimeException {
    public enum Reason { STALE_ENTRY_VERSION, STALE_VERSION, IDEMPOTENCY_MISMATCH, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public WithholdingConflictException(Reason reason) {super("Conflito na retenção financeira.");this.reason=reason;}
    public Reason reason() {return reason;}
}
