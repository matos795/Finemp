package com.finemp.assets;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AssetResponse>> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) AssetCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(
                        actor.getUserAccountId(),
                        companyId,
                        search,
                        status,
                        category,
                        page,
                        size));
    }

    @GetMapping("/summary")
    public ResponseEntity<AssetSummaryResponse> summary(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.summary(actor.getUserAccountId(), companyId));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<AssetResponse> find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID assetId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.find(actor.getUserAccountId(), companyId, assetId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody AssetRequest request) {

        return service.create(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{assetId}")
    public AssetResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetRequest request) {

        return service.update(actor.getUserAccountId(), companyId, assetId, request);
    }
}
