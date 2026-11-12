package com.finemp.sales.stage;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SalesStageRepository extends JpaRepository<SalesStage, UUID> {

    Optional<SalesStage> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select stage
            from SalesStage stage
            where stage.id = :id
              and stage.company.id = :companyId
            """)
    Optional<SalesStage> findLockedByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    Page<SalesStage> findByCompanyId(
            UUID companyId,
            Pageable pageable);

    Page<SalesStage> findByCompanyIdAndActive(
            UUID companyId,
            boolean active,
            Pageable pageable);
}
