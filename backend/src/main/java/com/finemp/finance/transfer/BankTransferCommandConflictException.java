package com.finemp.finance.transfer;

public class BankTransferCommandConflictException extends RuntimeException {
    public enum Reason { STALE_VERSION, IDEMPOTENCY_MISMATCH, TRANSACTION_NOT_SUPPORTED, INSUFFICIENT_ACTIVE_ACCOUNTS }
    private final Reason reason;
    public BankTransferCommandConflictException(Reason reason) {super("Conflito no comando de transferência: "+reason);this.reason=reason;}
    public Reason getReason() {return reason;}
}
