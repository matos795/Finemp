package com.finemp.accounting.reading;

/** Must obtain identity from the external source, never echo the expected identity. */
public interface AccountingCompanyIdentityReader {
    ExternalCompanyIdentity readIdentity(AccountingReadRequest request);
}

