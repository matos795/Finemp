package com.finemp.connector;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConnectorCredentialRepository extends JpaRepository<ConnectorCredential, UUID> {
    Optional<ConnectorCredential> findByTokenHash(String hash);
    boolean existsByAccountingSourceIdAndRevokedAtIsNull(UUID sourceId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from ConnectorCredential credential where credential.id = :id and credential.accountingFirmId = :firm")
    Optional<ConnectorCredential> findLocked(@Param("id") UUID id, @Param("firm") UUID firm);
    @Query("""
            select credential from ConnectorCredential credential where credential.accountingFirmId = :firm
              and (:search = '' or locate(lower(:search), lower(credential.label)) > 0)
            """)
    Page<ConnectorCredential> search(@Param("firm") UUID firm, @Param("search") String search, Pageable pageable);
}
