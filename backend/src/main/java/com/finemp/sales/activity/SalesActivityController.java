package com.finemp.sales.activity;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/sales/activities")
public class SalesActivityController {

    private final SalesActivityService service;

    public SalesActivityController(SalesActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<SalesActivityResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(required = false) UUID opportunityId,
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) SalesActivityFilter filter,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                opportunityId,
                partnerId,
                filter,
                search,
                page,
                size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesActivityResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody SalesActivityCreateRequest request) {

        return service.create(
                actor.getUserAccountId(),
                companyId,
                request);
    }

    @PutMapping("/{activityId}")
    public SalesActivityResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID activityId,
            @Valid @RequestBody SalesActivityUpdateRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                activityId,
                request);
    }

    @PostMapping("/{activityId}/state")
    public SalesActivityResponse command(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID activityId,
            @Valid @RequestBody SalesActivityCommandRequest request) {

        return service.command(
                actor.getUserAccountId(),
                companyId,
                activityId,
                request);
    }
}
