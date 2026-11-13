package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/diagnostics")
public class ConsistencyDiagnosticsController {

    private final ConsistencyDiagnosticsService diagnostics;

    public ConsistencyDiagnosticsController(
            ConsistencyDiagnosticsService diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping
    public ResponseEntity<ConsistencyDiagnosticsService.Report> report(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(diagnostics.report(
                        actor.getUserAccountId(),
                        companyId));
    }
}
