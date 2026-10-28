package com.finemp.finance.category;

public class FinancialCategoryConflictException extends RuntimeException {
    public enum Reason { STALE_VERSION, CATEGORY_IN_USE, NOT_A_GROUP, SEQUENCE_EXHAUSTED, PLAN_NOT_EMPTY, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public FinancialCategoryConflictException(Reason reason) {super("Conflito na categoria financeira.");this.reason=reason;}
    public Reason reason() {return reason;}
}
