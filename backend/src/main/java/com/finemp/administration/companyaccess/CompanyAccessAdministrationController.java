package com.finemp.administration.companyaccess;

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
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/admin/accounting-firms/{firmId}/clients/{clientId}/companies/{companyId}/users")
public class CompanyAccessAdministrationController {
    private final CompanyAccessAdministrationService service;
    public CompanyAccessAdministrationController(CompanyAccessAdministrationService service) { this.service = service; }

    @GetMapping
    public PagedResponse<UserAdministrationSummary> list(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID clientId, @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(principal.getUserAccountId(), firmId, clientId, companyId, search, page, size);
    }

    @GetMapping("/{userId}")
    public CompanyUserAccessResponse find(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID clientId, @PathVariable UUID companyId, @PathVariable UUID userId) {
        return service.find(principal.getUserAccountId(), firmId, clientId, companyId, userId);
    }

    @PutMapping("/{userId}")
    public CompanyUserAccessResponse replace(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID clientId, @PathVariable UUID companyId, @PathVariable UUID userId,
            @RequestBody @Valid SetCompanyAccessRequest request) {
        return service.replace(principal.getUserAccountId(), firmId, clientId, companyId, userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID firmId, @PathVariable UUID clientId, @PathVariable UUID companyId, @PathVariable UUID userId) {
        service.revoke(principal.getUserAccountId(), firmId, clientId, companyId, userId);
    }
}
