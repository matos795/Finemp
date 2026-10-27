package com.finemp.administration;

import com.finemp.company.Company;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        UUID clientId,
        String legalName,
        String cnpj) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getClient() == null ? null : company.getClient().getId(),
                company.getLegalName(),
                company.getCnpj());
    }
}
