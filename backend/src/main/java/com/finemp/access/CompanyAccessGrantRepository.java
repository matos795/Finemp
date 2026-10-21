package com.finemp.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyAccessGrantRepository
        extends JpaRepository<CompanyAccessGrant, UUID> {

    boolean existsByUserAccountIdAndCompanyId(
            UUID userAccountId,
            UUID companyId);
}