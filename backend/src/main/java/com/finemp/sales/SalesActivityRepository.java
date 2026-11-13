package com.finemp.sales;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SalesActivityRepository extends JpaRepository<SalesActivity, UUID> {

    @Query("""
            select activity from SalesActivity activity
            left join activity.businessPartner partner
            left join activity.opportunity opportunity
            where activity.company.id=:companyId
              and (:search='' or lower(activity.subject) like lower(concat('%',:search,'%'))
                   or lower(partner.profile.name) like lower(concat('%',:search,'%'))
                   or lower(opportunity.values.title) like lower(concat('%',:search,'%')))
              and (:completed is null
                   or (:completed=true and activity.completedAt is not null)
                   or (:completed=false and activity.completedAt is null))
            """)
    Page<SalesActivity> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("completed") Boolean completed,
            Pageable pageable);

    Optional<SalesActivity> findByIdAndCompanyId(UUID id, UUID companyId);

    List<SalesActivity> findAllByCompanyIdAndBusinessPartnerIdOrderByDueOnAsc(
            UUID companyId, UUID businessPartnerId);

    long countByCompanyIdAndBusinessPartnerIdAndCompletedAtIsNull(UUID companyId, UUID businessPartnerId);

    @Query("""
            select count(activity) from SalesActivity activity
            where activity.company.id=:companyId
              and activity.businessPartner.id=:partnerId
              and activity.completedAt is null
              and activity.dueOn < :today
            """)
    long countOverdueByPartner(
            @Param("companyId") UUID companyId,
            @Param("partnerId") UUID partnerId,
            @Param("today") LocalDate today);

    boolean existsByCompanyIdAndBusinessPartnerId(UUID companyId, UUID businessPartnerId);

    boolean existsByCompanyIdAndOpportunityIdAndBusinessPartnerIdNot(
            UUID companyId,
            UUID opportunityId,
            UUID businessPartnerId);
}
