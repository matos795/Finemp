package com.finemp.access;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CompanyPermissionAuthorizationService {

    private final CompanyAccessGrantRepository companyAccessGrantRepository;

    public CompanyPermissionAuthorizationService(
            CompanyAccessGrantRepository companyAccessGrantRepository) {

        this.companyAccessGrantRepository = companyAccessGrantRepository;
    }

    public boolean hasPermission(
            UUID userAccountId,
            UUID companyId,
            CompanyPermission permission) {

        return companyAccessGrantRepository
                .findByUserAccountIdAndCompanyId(
                        userAccountId,
                        companyId)
                .map(grant -> grant.getCostCenterScope() == CostCenterScope.ALL && grant.hasPermission(permission))
                .orElse(false);
    }
}
