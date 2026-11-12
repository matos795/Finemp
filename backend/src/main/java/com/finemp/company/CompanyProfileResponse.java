package com.finemp.company;

import java.util.UUID;

public record CompanyProfileResponse(
        UUID id,
        String legalName,
        String cnpj) {

    public static CompanyProfileResponse from(Company company) {
        return new CompanyProfileResponse(
                company.getId(),
                company.getLegalName(),
                company.getCnpj());
    }
}
