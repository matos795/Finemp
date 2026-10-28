package com.finemp.finance.period;

public class FinancialPeriodConflictException extends RuntimeException {
    public enum Reason { STALE_VERSION, IDEMPOTENCY_MISMATCH, ALREADY_CLOSED, ALREADY_OPEN, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public FinancialPeriodConflictException(Reason reason) {super("Conflito no período financeiro.");this.reason=reason;}
    public Reason reason() {return reason;}
}
