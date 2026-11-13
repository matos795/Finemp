package com.finemp.sales;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/sales")
public class SalesInsightsController {

    private final SalesInsightsService insights;
    private final OpportunityService opportunities;

    public SalesInsightsController(
            SalesInsightsService insights,
            OpportunityService opportunities) {
        this.insights = insights;
        this.opportunities = opportunities;
    }

    @GetMapping("/partners")
    public PagedResponse<SalesPartnerOptionResponse> partnerOptions(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return opportunities.partnerOptions(
                actor.getUserAccountId(),
                companyId,
                search,
                includeInactive,
                page,
                size);
    }

    @GetMapping("/indicators")
    public SalesIndicatorsResponse indicators(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId) {
        return insights.indicators(actor.getUserAccountId(), companyId);
    }

    @GetMapping("/partners/{partnerId}/profile")
    public SalesProfileResponse profile(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID partnerId) {
        return insights.profile(actor.getUserAccountId(), companyId, partnerId);
    }
}
