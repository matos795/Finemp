package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/working-capital-cycle")
public class WorkingCapitalCycleController {

    private final WorkingCapitalCycleService reports;

    public WorkingCapitalCycleController(WorkingCapitalCycleService reports) {
        this.reports = reports;
    }

    @GetMapping
    public ResponseEntity<WorkingCapitalCycleService.Report> report(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal inventoryDays) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reports.report(
                        actor.getUserAccountId(),
                        companyId,
                        from,
                        to,
                        inventoryDays));
    }
}
