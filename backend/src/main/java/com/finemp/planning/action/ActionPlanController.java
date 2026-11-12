package com.finemp.planning.action;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/action-plans")
public class ActionPlanController {

    private final ActionPlanService service;

    public ActionPlanController(ActionPlanService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<ActionPlanResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                status,
                page,
                size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActionPlanResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody ActionPlanCreateRequest request) {

        return service.create(
                actor.getUserAccountId(),
                companyId,
                request);
    }

    @PutMapping("/{actionId}")
    public ActionPlanResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID actionId,
            @Valid @RequestBody ActionPlanUpdateRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                actionId,
                request);
    }

    @PostMapping("/{actionId}/completion")
    public ActionPlanResponse completion(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID actionId,
            @Valid @RequestBody ActionCompletionRequest request) {

        return service.setCompleted(
                actor.getUserAccountId(),
                companyId,
                actionId,
                request);
    }

    @DeleteMapping("/{actionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID actionId,
            @RequestParam long expectedVersion) {

        service.delete(
                actor.getUserAccountId(),
                companyId,
                actionId,
                expectedVersion);
    }
}
