package com.finemp.accounting.account;

import java.util.UUID;

public class AccountingAccountNotFoundException
        extends RuntimeException {

    public AccountingAccountNotFoundException(UUID accountId) {
        super("Accounting account not found: " + accountId);
    }
}