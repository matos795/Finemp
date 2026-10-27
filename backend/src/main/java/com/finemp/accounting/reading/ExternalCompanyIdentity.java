package com.finemp.accounting.reading;

import java.util.Objects;

public record ExternalCompanyIdentity(String externalReference, String cnpj) {
    public ExternalCompanyIdentity { Objects.requireNonNull(externalReference); Objects.requireNonNull(cnpj); }
    public boolean matches(AccountingReadRequest request) {
        String canonical = cnpj.replaceAll("[.\\-/\\s]", "");
        return referenceMatches(request) && canonical.matches("[0-9]{14}")
                && canonical.equals(request.expectedCompanyCnpj());
    }
    private boolean referenceMatches(AccountingReadRequest request) {
        if (externalReference.equals(request.externalCompanyReference())) return true;
        if (request.sourceType() != com.finemp.accounting.source.AccountingSourceType.QUESTOR) return false;
        try {
            int observed = Integer.parseInt(externalReference);
            int expected = Integer.parseInt(request.externalCompanyReference());
            return observed > 0 && expected > 0 && observed == expected;
        } catch (NumberFormatException invalid) { return false; }
    }
    @Override public String toString() { return "ExternalCompanyIdentity[redacted]"; }
}

