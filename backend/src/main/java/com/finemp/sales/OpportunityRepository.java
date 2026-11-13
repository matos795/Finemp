package com.finemp.sales;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    @Query("""
            select opportunity from Opportunity opportunity
            left join opportunity.businessPartner partner
            where opportunity.company.id=:companyId
              and (:search='' or lower(opportunity.values.title) like lower(concat('%',:search,'%'))
                   or lower(partner.profile.name) like lower(concat('%',:search,'%')))
              and (:responsible='' or lower(opportunity.values.responsible)=lower(:responsible))
              and (:stage='' or lower(opportunity.values.stage)=lower(:stage))
              and (:status is null or opportunity.status=:status)
            """)
    Page<Opportunity> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("responsible") String responsible,
            @Param("stage") String stage,
            @Param("status") OpportunityStatus status,
            Pageable pageable);

    Optional<Opportunity> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Opportunity> findAllByCompanyId(UUID companyId);

    List<Opportunity> findAllByCompanyIdAndBusinessPartnerIdOrderByCreatedAtDesc(
            UUID companyId, UUID businessPartnerId);

    boolean existsByCompanyIdAndBusinessPartnerId(UUID companyId, UUID businessPartnerId);
}
