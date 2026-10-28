package com.finemp.finance.center;

public class ResultCenterConflictException extends RuntimeException {
    public enum Reason { STALE_VERSION, DUPLICATE_CODE, PARENT_NOT_FOUND, PARENT_HAS_ENTRIES, HAS_CHILDREN, PLAN_NOT_EMPTY, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public ResultCenterConflictException(Reason reason) {super("Conflito no centro de resultado.");this.reason=reason;}
    public Reason reason() {return reason;}
}
