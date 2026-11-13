package com.finemp.sales;

import java.time.LocalDate;
import java.util.UUID;

public record SalesProfileResponse(
        UUID businessPartnerId,
        String businessPartnerName,
        boolean active,
        long openOpportunities,
        long wonOpportunities,
        long lostOpportunities,
        long openActivities,
        long overdueActivities,
        LocalDate asOf) {
}
