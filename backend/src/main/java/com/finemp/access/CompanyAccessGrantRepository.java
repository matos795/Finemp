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
                        select accessGrant.userAccount from CompanyAccessGrant accessGrant
                        where accessGrant.company.id = :companyId
                          and (:search = ''
                               or locate(lower(:search), lower(accessGrant.userAccount.name)) > 0
                               or locate(lower(:search), lower(accessGrant.userAccount.email)) > 0)
                        order by accessGrant.userAccount.name, accessGrant.id
                        """)
        org.springframework.data.domain.Page<UserAccount> searchCompanyUsers(
                        @org.springframework.data.repository.query.Param("companyId") UUID companyId,
                        @org.springframework.data.repository.query.Param("search") String search,
                        org.springframework.data.domain.Pageable pageable);

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
