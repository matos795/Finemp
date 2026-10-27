package com.finemp.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountingFirmAccessGrantRepository
        extends JpaRepository<AccountingFirmAccessGrant, UUID> {

    @Query("""
            select case when count(accessGrant) > 0 then true else false end
            from AccountingFirmAccessGrant accessGrant
            join accessGrant.permissions permission
            where accessGrant.userAccount.id = :userAccountId
              and accessGrant.accountingFirm.id = :accountingFirmId
              and permission = :permission
              and accessGrant.userAccount.active = true
            """)
    boolean existsByUserAccountIdAndAccountingFirmIdAndPermission(
            @Param("userAccountId") UUID userAccountId,
            @Param("accountingFirmId") UUID accountingFirmId,
            @Param("permission") AccountingFirmPermission permission);

    java.util.Optional<AccountingFirmAccessGrant> findByUserAccountIdAndAccountingFirmId(
            UUID userAccountId, UUID accountingFirmId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"accountingFirm", "permissions"})
    java.util.List<AccountingFirmAccessGrant> findAllByUserAccountId(UUID userAccountId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "userAccount")
    @Query("""
            select accessGrant from AccountingFirmAccessGrant accessGrant
            where accessGrant.accountingFirm.id = :firmId
              and (:search = ''
                   or locate(lower(:search), lower(accessGrant.userAccount.name)) > 0
                   or locate(lower(:search), lower(accessGrant.userAccount.email)) > 0)
            """)
    org.springframework.data.domain.Page<AccountingFirmAccessGrant> searchMembers(
            @Param("firmId") UUID firmId, @Param("search") String search,
            org.springframework.data.domain.Pageable pageable);
}
