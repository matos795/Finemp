package com.finemp.sales;

import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SalesPartnerReferenceGuard implements BusinessPartnerReferenceGuard {

    private final OpportunityRepository opportunities;
    private final SalesActivityRepository activities;

    public SalesPartnerReferenceGuard(
            OpportunityRepository opportunities,
            SalesActivityRepository activities) {
        this.opportunities = opportunities;
        this.activities = activities;
    }

    @Override
    public boolean hasReferences(UUID companyId, UUID partnerId) {
        return opportunities.existsByCompanyIdAndBusinessPartnerId(companyId, partnerId)
                || activities.existsByCompanyIdAndBusinessPartnerId(companyId, partnerId);
    }
}
