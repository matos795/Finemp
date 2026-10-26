package com.finemp.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Query("select userAccount.id from UserAccount userAccount where lower(userAccount.email) = lower(:email)")
    Optional<UUID> findIdByEmailIgnoreCase(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select userAccount from UserAccount userAccount where userAccount.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") UUID id);

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
