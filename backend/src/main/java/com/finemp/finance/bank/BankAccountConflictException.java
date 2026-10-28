package com.finemp.finance.bank;

public final class BankAccountConflictException extends RuntimeException{
    public enum Reason { STALE_VERSION, LAST_ACCOUNT, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public BankAccountConflictException(Reason reason){super("Operação conflitante em conta financeira.");this.reason=reason;}
    public Reason reason(){return reason;}
}
