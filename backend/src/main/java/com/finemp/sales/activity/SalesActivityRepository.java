package com.finemp.sales.activity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SalesActivityRepository
        extends JpaRepository<SalesActivity, UUID> {

    Optional<SalesActivity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select activity
            from SalesActivity activity
            where activity.id = :id
              and activity.company.id = :companyId
            """)
    Optional<SalesActivity> findLockedByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    @Query("""
            select activity
            from SalesActivity activity
            left join activity.businessPartner partner
            where activity.company.id = :companyId
              and (:opportunityId is null or activity.opportunity.id = :opportunityId)
              and (:partnerId is null or partner.id = :partnerId)
              and (
                    :filter = 'ALL'
                    or (:filter = 'PENDING'
                        and activity.completedAt is null
                        and activity.cancelledAt is null)
                    or (:filter = 'TODAY'
                        and activity.completedAt is null
                        and activity.cancelledAt is null
                        and activity.dueOn = :today)
                    or (:filter = 'OVERDUE'
                        and activity.completedAt is null
                        and activity.cancelledAt is null
                        and activity.dueOn < :today)
                    or (:filter = 'COMPLETED'
                        and activity.completedAt is not null)
                    or (:filter = 'CANCELLED'
                        and activity.cancelledAt is not null)
              )
              and (
                    :search = ''
                    or lower(activity.title) like lower(concat('%', :search, '%'))
                    or lower(activity.responsible) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.name, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.tradeName, '')) like lower(concat('%', :search, '%'))
              )
            """)
    Page<SalesActivity> search(
            @Param("companyId") UUID companyId,
            @Param("opportunityId") UUID opportunityId,
            @Param("partnerId") UUID partnerId,
            @Param("filter") String filter,
            @Param("today") LocalDate today,
            @Param("search") String search,
            Pageable pageable);
}
