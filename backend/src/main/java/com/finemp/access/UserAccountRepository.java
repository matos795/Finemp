package com.finemp.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByIdAndActiveTrueAndCredentialsVersion(UUID id, long credentialsVersion);

    @Query("select userAccount.id from UserAccount userAccount where lower(userAccount.email) = lower(:email)")
    Optional<UUID> findIdByEmailIgnoreCase(@Param("email") String email);

    @Modifying
    @Query(value = """
            INSERT INTO user_accounts
                (id, name, email, password_hash, active, invitation_pending, credentials_version, created_at)
            VALUES (:id, :name, :email, '!pending-invitation', FALSE, TRUE, 0, :createdAt)
            ON CONFLICT (LOWER(email)) DO NOTHING
            """, nativeQuery = true)
    int insertInvitedIfAbsent(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("email") String email,
            @Param("createdAt") Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select userAccount from UserAccount userAccount where userAccount.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") UUID id);

    @Query("""
            select case when count(userAccount) > 0 then true else false end
            from UserAccount userAccount
            join userAccount.platformPermissions permission
            where userAccount.id = :userAccountId
              and permission = :permission
              and userAccount.active = true
            """)
    boolean existsByIdAndPlatformPermission(
            @Param("userAccountId") UUID userAccountId,
            @Param("permission") PlatformPermission permission);

    @Query("""
            select count(distinct userAccount.id) from UserAccount userAccount
            join userAccount.platformPermissions permission
            where userAccount.active = true and permission = :permission
            """)
    long countActiveByPlatformPermission(@Param("permission") PlatformPermission permission);

    @Query("""
            select userAccount from UserAccount userAccount
            where :search = ''
               or locate(lower(:search), lower(userAccount.name)) > 0
               or locate(lower(:search), lower(userAccount.email)) > 0
            """)
    org.springframework.data.domain.Page<UserAccount> searchForAdministration(
            @Param("search") String search, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select count(distinct account.id) from UserAccount account
            join account.platformPermissions permission
            where account.active = true and permission in :permissions
            """)
    long countActiveWithAnyPermission(@Param("permissions") java.util.Set<PlatformPermission> permissions);
}
