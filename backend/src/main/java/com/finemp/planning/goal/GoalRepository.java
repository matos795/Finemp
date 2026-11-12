package com.finemp.planning.goal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select goal
            from Goal goal
            where goal.id = :id
              and goal.company.id = :companyId
            """)
    Optional<Goal> findLockedByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    @Query("""
            select goal
            from Goal goal
            where goal.company.id = :companyId
              and (:active is null or goal.active = :active)
            """)
    Page<Goal> search(
            @Param("companyId") UUID companyId,
            @Param("active") Boolean active,
            Pageable pageable);
}
