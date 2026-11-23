package com.finemp.accounting.sync.status;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompanySyncStatusController {

    private final CompanySyncStatusService service;

    public CompanySyncStatusController(CompanySyncStatusService service) {
        this.service = service;
    }

    @GetMapping("/api/companies/{companyId}/accounting-sources")
    public ResponseEntity<CompanySyncStatusResponse> list(@AuthenticationPrincipal UserAccountPrincipal user,
                                                          @PathVariable UUID companyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findForCompany(user.getUserAccountId(), companyId));
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso à sincronização não autorizado.");
    }
}
