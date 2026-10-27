package com.finemp.administration;

import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceType;

import java.util.UUID;

public record AccountingSourceResponse(
        UUID id,
        UUID companyId,
        AccountingSourceType type,
        String externalReference,
        boolean active) {

    public static AccountingSourceResponse from(AccountingSource source) {
        return new AccountingSourceResponse(
                source.getId(),
                source.getCompany().getId(),
                source.getType(),
                source.getExternalReference(),
                source.isActive());
    }
}
