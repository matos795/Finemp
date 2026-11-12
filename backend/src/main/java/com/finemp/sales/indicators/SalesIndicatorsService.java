package com.finemp.sales.indicators;

import com.finemp.sales.SalesAuthorization;
import com.finemp.sales.opportunity.OpportunityRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesIndicatorsService {

    private final OpportunityRepository opportunities;
    private final SalesAuthorization authorization;
    private final Clock clock;

    public SalesIndicatorsService(
            OpportunityRepository opportunities,
            SalesAuthorization authorization,
            Clock clock) {
        this.opportunities = opportunities;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalesIndicatorsResponse get(
            UUID actorId,
            UUID companyId) {

        authorization.requireRead(actorId, companyId);

        var metrics = opportunities.indicatorRows(companyId)
                .stream()
                .map(OpportunityIndicatorRow::toMetric)
                .toList();

        return SalesIndicatorsResponse.from(
                SalesIndicatorsCalculator.calculate(
                        metrics,
                        LocalDate.now(clock)));
    }
}
