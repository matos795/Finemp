package com.finemp.sales.opportunity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityStageHistoryRepository
        extends JpaRepository<OpportunityStageHistory, UUID> {

    Page<OpportunityStageHistory> findByCompanyIdAndOpportunityId(
            UUID companyId,
            UUID opportunityId,
            Pageable pageable);

    Optional<OpportunityStageHistory>
            findFirstByCompanyIdAndOpportunityIdOrderByStagePositionDescOccurredAtDescIdDesc(
                    UUID companyId,
                    UUID opportunityId);
}
