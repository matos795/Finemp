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
            """)
    boolean existsByUserAccountIdAndAccountingFirmIdAndPermission(
            @Param("userAccountId") UUID userAccountId,
            @Param("accountingFirmId") UUID accountingFirmId,
            @Param("permission") AccountingFirmPermission permission);
}

