package com.finemp.access;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CompanyPermissionAuthorizationService {

    private final PlatformPermissionAuthorizationService platform;
    private final CompanyAccessGrantRepository companyAccessGrantRepository;

    public CompanyPermissionAuthorizationService(
            CompanyAccessGrantRepository companyAccessGrantRepository, PlatformPermissionAuthorizationService platform) {

        this.platform = platform;
        this.companyAccessGrantRepository = companyAccessGrantRepository;
    }

    public boolean hasPermission(
            UUID userAccountId,
            UUID companyId,
            CompanyPermission permission) {

        return platform.isSystemAdmin(userAccountId) || companyAccessGrantRepository
                .findByUserAccountIdAndCompanyId(
                        userAccountId,
                        companyId)
                .map(grant -> grant.getCostCenterScope() == CostCenterScope.ALL && grant.hasPermission(permission))
                .orElse(false);
    }

    // Scalar query intentionally bypasses already-initialized grants in the JPA persistence context.
    public boolean hasFreshPermission(UUID userAccountId, UUID companyId, CompanyPermission permission) {
        return platform.isSystemAdmin(userAccountId) || companyAccessGrantRepository.existsCurrentPermission(userAccountId, companyId, CostCenterScope.ALL, permission);
    }
}
