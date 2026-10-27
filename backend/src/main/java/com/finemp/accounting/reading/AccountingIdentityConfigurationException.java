package com.finemp.accounting.reading;
public class AccountingIdentityConfigurationException extends RuntimeException {
    public AccountingIdentityConfigurationException() { super("External identity mapping is unavailable or unsupported."); }
}
