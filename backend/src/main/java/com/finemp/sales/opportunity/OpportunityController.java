package com.finemp.sales.opportunity;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/sales/opportunities")
public class OpportunityController {

    private final OpportunityService service;

    public OpportunityController(OpportunityService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<OpportunityResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(required = false) OpportunityOutcome outcome,
            @RequestParam(required = false) UUID stageId,
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(defaultValue = "") String responsible,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                outcome,
                stageId,
                partnerId,
                responsible,
                search,
                page,
                size);
    }

    @GetMapping("/{opportunityId}")
    public OpportunityResponse find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId) {

        return service.find(
                actor.getUserAccountId(),
                companyId,
                opportunityId);
    }

    @GetMapping("/{opportunityId}/stage-history")
    public PagedResponse<OpportunityStageHistoryResponse> history(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.history(
                actor.getUserAccountId(),
                companyId,
                opportunityId,
                page,
                size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpportunityResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody OpportunityCreateRequest request) {

        return service.create(
                actor.getUserAccountId(),
                companyId,
                request);
    }

    @PutMapping("/{opportunityId}")
    public OpportunityResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityUpdateRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                opportunityId,
                request);
    }

    @PostMapping("/{opportunityId}/stage")
    public OpportunityResponse moveStage(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityMoveStageRequest request) {

        return service.moveStage(
                actor.getUserAccountId(),
                companyId,
                opportunityId,
                request);
    }

    @PostMapping("/{opportunityId}/outcome")
    public OpportunityResponse outcome(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityOutcomeRequest request) {

        return service.setOutcome(
                actor.getUserAccountId(),
                companyId,
                opportunityId,
                request);
    }
}
