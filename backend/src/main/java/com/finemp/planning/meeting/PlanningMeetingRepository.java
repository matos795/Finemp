package com.finemp.planning.meeting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PlanningMeetingRepository
        extends JpaRepository<PlanningMeeting, UUID> {

    Optional<PlanningMeeting> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select meeting
            from PlanningMeeting meeting
            where meeting.id = :id
              and meeting.company.id = :companyId
            """)
    Optional<PlanningMeeting> findLockedByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    @Query("""
            select meeting
            from PlanningMeeting meeting
            where meeting.company.id = :companyId
              and (:stage = '' or lower(meeting.stage) = lower(:stage))
            """)
    Page<PlanningMeeting> search(
            @Param("companyId") UUID companyId,
            @Param("stage") String stage,
            Pageable pageable);
}
