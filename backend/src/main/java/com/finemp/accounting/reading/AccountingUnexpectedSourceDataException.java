package com.finemp.accounting.reading;

/**
 * Sanitized category for source data that violates an adapter's confirmed invariants.
 * Carries no source values, keys, SQL, paths or credentials.
 */
public class AccountingUnexpectedSourceDataException extends RuntimeException {
    public AccountingUnexpectedSourceDataException() {
        super("Unexpected accounting source data.");
    }
}
