package com.finemp.access;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountingFirmPermissionAuthorizationService {

    private final AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository;

    public AccountingFirmPermissionAuthorizationService(
            AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository) {
        this.accountingFirmAccessGrantRepository = accountingFirmAccessGrantRepository;
    }

    public boolean hasPermission(
            UUID userAccountId,
            UUID accountingFirmId,
            AccountingFirmPermission permission) {
        return accountingFirmAccessGrantRepository
                .existsByUserAccountIdAndAccountingFirmIdAndPermission(
                        userAccountId,
                        accountingFirmId,
                        permission);
    }
}

