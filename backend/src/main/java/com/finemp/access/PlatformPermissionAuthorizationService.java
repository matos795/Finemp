package com.finemp.access;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlatformPermissionAuthorizationService {

    private final UserAccountRepository userAccountRepository;

    public PlatformPermissionAuthorizationService(
            UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public boolean hasPermission(
            UUID userAccountId,
            PlatformPermission permission) {
        return userAccountRepository.existsByIdAndPlatformPermission(userAccountId, permission)
                || (permission != PlatformPermission.SYSTEM_ADMIN && isSystemAdmin(userAccountId));
    }

    public boolean isSystemAdmin(UUID userAccountId) {
        return userAccountRepository.existsByIdAndPlatformPermission(userAccountId, PlatformPermission.SYSTEM_ADMIN);
    }
}

