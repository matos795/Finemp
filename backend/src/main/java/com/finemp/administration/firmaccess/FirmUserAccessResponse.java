package com.finemp.administration.firmaccess;

import com.finemp.access.AccountingFirmPermission;
import com.finemp.administration.users.UserAdministrationSummary;
import java.util.Set;
import java.util.UUID;

public record FirmUserAccessResponse(UUID accountingFirmId, UserAdministrationSummary user,
                                     Set<AccountingFirmPermission> permissions) {
    public FirmUserAccessResponse { permissions = Set.copyOf(permissions); }
}
