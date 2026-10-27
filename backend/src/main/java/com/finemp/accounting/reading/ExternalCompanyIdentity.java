package com.finemp.accounting.reading;

import java.util.Objects;

public record ExternalCompanyIdentity(String externalReference, String cnpj) {
    public ExternalCompanyIdentity { Objects.requireNonNull(externalReference); Objects.requireNonNull(cnpj); }
    public boolean matches(AccountingReadRequest request) {
        String canonical = cnpj.replaceAll("[.\\-/\\s]", "");
        return externalReference.equals(request.externalCompanyReference()) && canonical.matches("[0-9]{14}")
                && canonical.equals(request.expectedCompanyCnpj());
    }
    @Override public String toString() { return "ExternalCompanyIdentity[redacted]"; }
}

