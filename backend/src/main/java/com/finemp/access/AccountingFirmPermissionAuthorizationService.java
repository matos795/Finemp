package com.finemp.access;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class AccountingFirmPermissionAuthorizationService {

    private final PlatformPermissionAuthorizationService platform;
    private final AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository;

    public AccountingFirmPermissionAuthorizationService(
            AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository, PlatformPermissionAuthorizationService platform) {
        this.platform = platform;
        this.accountingFirmAccessGrantRepository = accountingFirmAccessGrantRepository;
    }

    public boolean hasPermission(
            UUID userAccountId,
            UUID accountingFirmId,
            AccountingFirmPermission permission) {
        return platform.isSystemAdmin(userAccountId) || accountingFirmAccessGrantRepository
                .existsByUserAccountIdAndAccountingFirmIdAndPermission(
                        userAccountId,
                        accountingFirmId,
                        permission);
    }

    /** SYSTEM_ADMIN ou ao menos uma das permissões na contabilidade. */
    public boolean hasAnyPermission(
            UUID userAccountId,
            UUID accountingFirmId,
            Set<AccountingFirmPermission> permissions) {
        return platform.isSystemAdmin(userAccountId) || accountingFirmAccessGrantRepository
                .existsByUserAccountIdAndAccountingFirmIdAndAnyPermission(
                        userAccountId,
                        accountingFirmId,
                        permissions);
    }
}
