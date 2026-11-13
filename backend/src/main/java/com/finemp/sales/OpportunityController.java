package com.finemp.sales;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/opportunities")
public class OpportunityController {

    private final OpportunityService service;

    public OpportunityController(OpportunityService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<OpportunitySummaryResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String responsible,
            @RequestParam(defaultValue = "") String stage,
            @RequestParam(required = false) OpportunityStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(
                actor.getUserAccountId(),
                companyId,
                search,
                responsible,
                stage,
                status,
                page,
                size);
    }

    @GetMapping("/{opportunityId}")
    public OpportunityResponse find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId) {
        return service.find(actor.getUserAccountId(), companyId, opportunityId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpportunityResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody OpportunityRequest request) {
        return service.create(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{opportunityId}")
    public OpportunityResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityRequest request) {
        return service.update(actor.getUserAccountId(), companyId, opportunityId, request);
    }

    @PostMapping("/{opportunityId}/stage")
    public OpportunityResponse moveStage(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityStageRequest request) {
        return service.moveStage(actor.getUserAccountId(), companyId, opportunityId, request);
    }

    @PostMapping("/{opportunityId}/status")
    public OpportunityResponse changeStatus(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityStatusRequest request) {
        return service.changeStatus(actor.getUserAccountId(), companyId, opportunityId, request);
    }
}
