package com.finemp.accounting.reading;

public class AccountingReadLimitExceededException extends RuntimeException {
    public AccountingReadLimitExceededException() { super("Accounting read exceeds the configured resource limit."); }
}
