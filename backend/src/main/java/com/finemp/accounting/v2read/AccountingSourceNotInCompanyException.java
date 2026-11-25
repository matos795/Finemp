package com.finemp.accounting.v2read;

/** Fonte inexistente ou de outra Company: as duas respondem igual, sem revelar existência. */
class AccountingSourceNotInCompanyException extends RuntimeException {
    AccountingSourceNotInCompanyException() {
        super("Accounting source not found.");
    }
}
