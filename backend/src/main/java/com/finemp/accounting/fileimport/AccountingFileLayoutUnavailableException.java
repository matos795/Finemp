package com.finemp.accounting.fileimport;

public final class AccountingFileLayoutUnavailableException extends RuntimeException {
    public AccountingFileLayoutUnavailableException() {
        super("Accounting file layout is not available.");
    }
}
