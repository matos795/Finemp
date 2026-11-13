package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/working-capital-cycle")
public class WorkingCapitalCycleController {

    private final WorkingCapitalCycleService service;

    public WorkingCapitalCycleController(WorkingCapitalCycleService service) {
        this.service = service;
    }

    @GetMapping
    public WorkingCapitalCycleCalculator.Report report(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "3") int months,
            @RequestParam(defaultValue = "0") int pmeDays) {
        return service.report(actor.getUserAccountId(), companyId, months, pmeDays);
    }
}
