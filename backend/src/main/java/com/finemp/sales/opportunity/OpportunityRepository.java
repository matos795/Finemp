package com.finemp.sales.opportunity;

import com.finemp.sales.indicators.OpportunityIndicatorRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    Optional<Opportunity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select opportunity
            from Opportunity opportunity
            where opportunity.id = :id
              and opportunity.company.id = :companyId
            """)
    Optional<Opportunity> findLockedByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    @Query("""
            select opportunity
            from Opportunity opportunity
            left join opportunity.businessPartner partner
            where opportunity.company.id = :companyId
              and (:outcome is null or opportunity.outcome = :outcome)
              and (:stageId is null or opportunity.stage.id = :stageId)
              and (:partnerId is null or partner.id = :partnerId)
              and (:responsible = '' or lower(opportunity.responsible) = lower(:responsible))
              and (
                    :search = ''
                    or lower(opportunity.title) like lower(concat('%', :search, '%'))
                    or lower(opportunity.responsible) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.name, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.tradeName, '')) like lower(concat('%', :search, '%'))
              )
            """)
    Page<Opportunity> search(
            @Param("companyId") UUID companyId,
            @Param("outcome") OpportunityOutcome outcome,
            @Param("stageId") UUID stageId,
            @Param("partnerId") UUID partnerId,
            @Param("responsible") String responsible,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select new com.finemp.sales.indicators.OpportunityIndicatorRow(
                opportunity.amount,
                opportunity.monthlyRecurring,
                opportunity.probability,
                opportunity.outcome,
                opportunity.openedOn,
                opportunity.closedOn,
                opportunity.expectedCloseOn
            )
            from Opportunity opportunity
            where opportunity.company.id = :companyId
            """)
    List<OpportunityIndicatorRow> indicatorRows(
            @Param("companyId") UUID companyId);
}
