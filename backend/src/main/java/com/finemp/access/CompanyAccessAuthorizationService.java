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

    public void requireAccess(
            UUID userAccountId,
            UUID companyId) {

        boolean hasAccess = companyAccessGrantRepository
                .existsByUserAccountIdAndCompanyId(
                        userAccountId,
                        companyId);

        if (!hasAccess) {
            throw new CompanyAccessDeniedException();
        }
    }
}