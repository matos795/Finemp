package com.finemp.accounting.reading;
public class AccountingIdentityMismatchException extends RuntimeException {
    public AccountingIdentityMismatchException() { super("External company identity could not be verified."); }
}
