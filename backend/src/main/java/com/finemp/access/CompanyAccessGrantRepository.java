package com.finemp.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyAccessGrantRepository
                extends JpaRepository<CompanyAccessGrant, UUID> {

        boolean existsByUserAccountIdAndCompanyId(
                        UUID userAccountId,
                        UUID companyId);

        boolean existsByUserAccountIdAndCompanyIdAndCostCenterScope(
                        UUID userAccountId,
                        UUID companyId,
                        CostCenterScope costCenterScope);

        List<CompanyAccessGrant> findAllByUserAccountId(
                        UUID userAccountId);

        Optional<CompanyAccessGrant> findByUserAccountIdAndCompanyId(
                        UUID userAccountId,
                        UUID companyId);

        @org.springframework.data.jpa.repository.Query("""
                        select case when count(accessGrant) > 0 then true else false end
                        from CompanyAccessGrant accessGrant
                        where accessGrant.userAccount.id = :userId
                          and accessGrant.company.client.accountingFirm.id = :firmId
                        """)
        boolean existsWithinAccountingFirm(
                        @org.springframework.data.repository.query.Param("userId") UUID userId,
                        @org.springframework.data.repository.query.Param("firmId") UUID firmId);
}
