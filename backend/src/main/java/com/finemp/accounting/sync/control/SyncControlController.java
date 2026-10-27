package com.finemp.accounting.sync.control;

import com.finemp.accounting.sync.SyncRunStatus;
import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@ConditionalOnProperty(prefix = "finemp.sync.control", name = "enabled", havingValue = "true")
@RequestMapping("/api/accounting-sources/{accountingSourceId}/sync-runs")
public class SyncControlController {
    private final SyncControlService service;
    private final SyncAttemptQueryService attempts;
    public SyncControlController(SyncControlService service, SyncAttemptQueryService attempts) { this.service = service; this.attempts = attempts; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncRunResponse request(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID accountingSourceId,
            @RequestHeader("Idempotency-Key") UUID key, @RequestBody @Valid RequestSyncRun request) {
        return service.request(user.getUserAccountId(), accountingSourceId, key, request);
    }
    @GetMapping
    public PagedResponse<SyncRunResponse> list(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID accountingSourceId,
            @RequestParam(required = false) SyncRunStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user.getUserAccountId(), accountingSourceId, status, page, size);
    }
    @GetMapping("/{runId}")
    public SyncRunResponse find(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID accountingSourceId, @PathVariable UUID runId) {
        return service.find(user.getUserAccountId(), accountingSourceId, runId);
    }
    @GetMapping("/{runId}/events")
    public PagedResponse<SyncRunEventResponse> events(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID accountingSourceId,
            @PathVariable UUID runId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.events(user.getUserAccountId(), accountingSourceId, runId, page, size);
    }
    @GetMapping("/{runId}/attempts")
    public PagedResponse<SyncAttemptResponse> attempts(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID accountingSourceId,
            @PathVariable UUID runId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return attempts.list(user.getUserAccountId(), accountingSourceId, runId, page, size);
    }
    @DeleteMapping("/{runId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID accountingSourceId, @PathVariable UUID runId) {
        service.cancel(user.getUserAccountId(), accountingSourceId, runId);
    }
}

