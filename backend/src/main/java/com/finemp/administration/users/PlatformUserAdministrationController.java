package com.finemp.administration.users;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/users")
public class PlatformUserAdministrationController {

    private final PlatformUserAdministrationService service;

    public PlatformUserAdministrationController(PlatformUserAdministrationService service) { this.service = service; }

    @GetMapping
    public PagedResponse<UserAdministrationSummary> list(@AuthenticationPrincipal UserAccountPrincipal principal,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(principal.getUserAccountId(), search, page, size);
    }

    @GetMapping("/{userId}")
    public UserAdministrationDetail find(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable UUID userId) {
        return service.find(principal.getUserAccountId(), userId);
    }

    @PatchMapping("/{userId}/status")
    public UserAdministrationSummary setActive(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID userId, @Valid @RequestBody SetUserActiveRequest request) {
        return service.setActive(principal.getUserAccountId(), userId, request.active());
    }

    @PutMapping("/{userId}/platform-permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replacePermissions(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID userId, @Valid @RequestBody SetPlatformPermissionsRequest request) {
        service.replacePermissions(principal.getUserAccountId(), userId, request.permissions());
    }
}
