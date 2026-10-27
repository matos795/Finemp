package com.finemp.administration;

import com.finemp.accountingfirm.AccountingFirm;

import java.util.UUID;

public record AccountingFirmResponse(
        UUID id,
        String name,
        boolean active) {

    public static AccountingFirmResponse from(AccountingFirm accountingFirm) {
        return new AccountingFirmResponse(
                accountingFirm.getId(),
                accountingFirm.getName(),
                accountingFirm.isActive());
    }
}
