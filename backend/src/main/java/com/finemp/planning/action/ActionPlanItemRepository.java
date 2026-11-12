package com.finemp.planning.action;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ActionPlanItemRepository
        extends JpaRepository<ActionPlanItem, UUID> {

    Optional<ActionPlanItem> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item
            from ActionPlanItem item
            where item.id = :id
              and item.company.id = :companyId
            """)
    Optional<ActionPlanItem> findLockedByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    @Query("""
            select item
            from ActionPlanItem item
            where item.company.id = :companyId
              and (:status = '' or lower(item.workflowStatus) = lower(:status))
            """)
    Page<ActionPlanItem> search(
            @Param("companyId") UUID companyId,
            @Param("status") String status,
            Pageable pageable);
}
