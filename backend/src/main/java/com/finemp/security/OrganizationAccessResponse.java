package com.finemp.security;

import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.PlatformPermission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record OrganizationAccessResponse(
        Set<PlatformPermission> platformPermissions,
        List<AccountingFirmAccess> accountingFirms) {

    public OrganizationAccessResponse {
        platformPermissions = Set.copyOf(platformPermissions);
        accountingFirms = List.copyOf(accountingFirms);
    }

    public record AccountingFirmAccess(
            UUID accountingFirmId,
            String name,
            boolean active,
            Set<AccountingFirmPermission> permissions) {
        public AccountingFirmAccess { permissions = Set.copyOf(permissions); }
    }
}
