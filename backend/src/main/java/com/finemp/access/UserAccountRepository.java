package com.finemp.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Query("""
            select case when count(userAccount) > 0 then true else false end
            from UserAccount userAccount
            join userAccount.platformPermissions permission
            where userAccount.id = :userAccountId
              and permission = :permission
            """)
    boolean existsByIdAndPlatformPermission(
            @Param("userAccountId") UUID userAccountId,
            @Param("permission") PlatformPermission permission);
}

