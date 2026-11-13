package com.finemp.sales;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/sales-activities")
public class SalesActivityController {

    private final SalesActivityService service;

    public SalesActivityController(SalesActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<SalesActivityResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(
                actor.getUserAccountId(),
                companyId,
                search,
                completed,
                page,
                size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesActivityResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody SalesActivityRequest request) {
        return service.create(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{activityId}")
    public SalesActivityResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID activityId,
            @Valid @RequestBody SalesActivityRequest request) {
        return service.update(actor.getUserAccountId(), companyId, activityId, request);
    }

    @PostMapping("/{activityId}/completion")
    public SalesActivityResponse completion(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID activityId,
            @Valid @RequestBody SalesActivityCompletionRequest request) {
        return service.changeCompletion(
                actor.getUserAccountId(),
                companyId,
                activityId,
                request);
    }

    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID activityId,
            @RequestParam long expectedVersion) {
        service.delete(actor.getUserAccountId(), companyId, activityId, expectedVersion);
    }
}
