package com.finemp.accounting.reading;

public class AccountingReaderUnavailableException extends RuntimeException {
    public AccountingReaderUnavailableException() { super("Adapter contábil indisponível."); }
}
