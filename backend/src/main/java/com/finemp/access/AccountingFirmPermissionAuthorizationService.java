package com.finemp.access;

import org.springframework.stereotype.Service;

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
}

