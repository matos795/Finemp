package com.finemp.administration.firmaccess;

import com.finemp.administration.PagedResponse;
import com.finemp.administration.users.UserAdministrationSummary;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/accounting-firms/{firmId}/users")
public class FirmAccessAdministrationController {

    private final FirmAccessAdministrationService service;

    public FirmAccessAdministrationController(FirmAccessAdministrationService service) { this.service = service; }

    @GetMapping
    public PagedResponse<UserAdministrationSummary> list(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(principal.getUserAccountId(), firmId, search, page, size);
    }

    @GetMapping("/{userId}")
    public FirmUserAccessResponse find(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID userId) {
        return service.find(principal.getUserAccountId(), firmId, userId);
    }

    @PutMapping("/{userId}")
    public FirmUserAccessResponse replace(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID userId, @Valid @RequestBody SetFirmPermissionsRequest request) {
        return service.replace(principal.getUserAccountId(), firmId, userId, request.permissions());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID userId) {
        service.revoke(principal.getUserAccountId(), firmId, userId);
    }
}
