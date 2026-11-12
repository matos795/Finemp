package com.finemp.planning.meeting;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/planning/meetings")
public class PlanningMeetingController {

    private final PlanningMeetingService service;

    public PlanningMeetingController(PlanningMeetingService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<PlanningMeetingResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                stage,
                page,
                size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanningMeetingResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody PlanningMeetingCreateRequest request) {

        return service.create(
                actor.getUserAccountId(),
                companyId,
                request);
    }

    @PutMapping("/{meetingId}")
    public PlanningMeetingResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID meetingId,
            @Valid @RequestBody PlanningMeetingUpdateRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                meetingId,
                request);
    }

    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID meetingId,
            @RequestParam long expectedVersion) {

        service.delete(
                actor.getUserAccountId(),
                companyId,
                meetingId,
                expectedVersion);
    }
}
