package com.finemp.access;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CompanyAccessAuthorizationService {

    private final CompanyAccessGrantRepository companyAccessGrantRepository;

    public CompanyAccessAuthorizationService(
            CompanyAccessGrantRepository companyAccessGrantRepository) {

        this.companyAccessGrantRepository = companyAccessGrantRepository;
    }

    public boolean hasAccess(
            UUID userAccountId,
            UUID companyId) {

        return companyAccessGrantRepository
                .existsByUserAccountIdAndCompanyIdAndCostCenterScope(
                        userAccountId,
                        companyId,
                        CostCenterScope.ALL);
    }

    public void requireAccess(
            UUID userAccountId,
            UUID companyId) {

        if (!hasAccess(userAccountId, companyId)) {
            throw new CompanyAccessDeniedException();
        }
    }
}
