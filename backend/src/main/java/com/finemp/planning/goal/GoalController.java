package com.finemp.planning.goal;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<GoalResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                active,
                page,
                size);
    }

    @GetMapping("/{goalId}")
    public GoalResponse find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID goalId) {

        return service.find(
                actor.getUserAccountId(),
                companyId,
                goalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody GoalCreateRequest request) {

        return service.create(
                actor.getUserAccountId(),
                companyId,
                request);
    }

    @PutMapping("/{goalId}")
    public GoalResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID goalId,
            @Valid @RequestBody GoalUpdateRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                goalId,
                request);
    }

    @DeleteMapping("/{goalId}")
    public GoalResponse deactivate(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID goalId,
            @RequestParam long expectedVersion) {

        return service.deactivate(
                actor.getUserAccountId(),
                companyId,
                goalId,
                expectedVersion);
    }

    @GetMapping("/{goalId}/measurements")
    public PagedResponse<GoalMeasurementResponse> measurements(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID goalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.measurements(
                actor.getUserAccountId(),
                companyId,
                goalId,
                page,
                size);
    }

    @PostMapping("/{goalId}/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalMeasurementResponse measure(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID goalId,
            @Valid @RequestBody GoalMeasurementRequest request) {

        return service.measure(
                actor.getUserAccountId(),
                companyId,
                goalId,
                request);
    }
}
