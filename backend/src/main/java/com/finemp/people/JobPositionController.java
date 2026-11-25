package com.finemp.people;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/job-positions")
public class JobPositionController {

    private final PeopleService service;

    public JobPositionController(PeopleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<JobPositionResponse>> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listPositions(actor.getUserAccountId(), companyId, search, active, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPositionResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestBody JobPositionRequest request) {
        return service.createPosition(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{positionId}")
    public JobPositionResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID positionId,
            @RequestBody JobPositionRequest request) {
        return service.updatePosition(actor.getUserAccountId(), companyId, positionId, request);
    }
}
