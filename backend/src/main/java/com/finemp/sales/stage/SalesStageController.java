package com.finemp.sales.stage;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/sales/stages")
public class SalesStageController {

    private final SalesStageService service;

    public SalesStageController(SalesStageService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<SalesStageResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                active,
                page,
                size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesStageResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody SalesStageCreateRequest request) {

        return service.create(
                actor.getUserAccountId(),
                companyId,
                request);
    }

    @PutMapping("/{stageId}")
    public SalesStageResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID stageId,
            @Valid @RequestBody SalesStageUpdateRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                stageId,
                request);
    }

    @DeleteMapping("/{stageId}")
    public SalesStageResponse deactivate(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID stageId,
            @RequestParam long expectedVersion) {

        return service.deactivate(
                actor.getUserAccountId(),
                companyId,
                stageId,
                expectedVersion);
    }
}
