package com.finemp.accounting.classification;

import java.util.UUID;

public class AccountingAccountDoesNotBelongToCompanyException
        extends RuntimeException {

    public AccountingAccountDoesNotBelongToCompanyException(
            UUID accountId,
            UUID companyId) {

        super(
                "Accounting account "
                        + accountId
                        + " does not belong to company "
                        + companyId);
    }
}