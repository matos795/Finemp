package com.finemp.accounting.reading;

import com.finemp.accounting.source.AccountingSourceType;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Expected CNPJ is an expectation, NOT proof of the external company's identity. */
public record AccountingReadRequest(UUID accountingSourceId, UUID companyId, AccountingSourceType sourceType,
        String externalCompanyReference, String expectedCompanyCnpj, LocalDate startDate, LocalDate endDate) {
    public AccountingReadRequest {
        Objects.requireNonNull(accountingSourceId);
        Objects.requireNonNull(companyId);
        Objects.requireNonNull(sourceType);
        if (externalCompanyReference == null || externalCompanyReference.isBlank())
            throw new IllegalArgumentException("Referência externa obrigatória.");
        if (expectedCompanyCnpj == null || !expectedCompanyCnpj.matches("[0-9]{14}"))
            throw new IllegalArgumentException("Identidade esperada da empresa inválida.");
        if (startDate == null || endDate == null || startDate.isAfter(endDate))
            throw new IllegalArgumentException("Período contábil inválido.");
    }
    @Override public String toString() {
        return "AccountingReadRequest[accountingSourceId=" + accountingSourceId + ", sourceType=" + sourceType + "]";
    }
}
