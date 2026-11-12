package com.finemp.audit;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api")
public class AuditEventQueryController {

    private final AuditEventQueryService service;

    public AuditEventQueryController(AuditEventQueryService service) {
        this.service = service;
    }

    @GetMapping("/admin/audit-events")
    public PagedResponse<AuditEventResponse> listGlobal(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {

        return service.listGlobal(principal.getUserAccountId(), page, size);
    }

    @GetMapping("/companies/{companyId}/audit-events")
    public PagedResponse<AuditEventResponse> listCompany(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {

        return service.listCompany(principal.getUserAccountId(), companyId, page, size);
    }
}
