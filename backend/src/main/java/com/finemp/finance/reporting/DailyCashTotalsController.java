package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/daily-cash-totals")
@ConditionalOnProperty(prefix="finemp.finance.daily-cash",name="enabled",havingValue="true")
public class DailyCashTotalsController {
    private final DailyCashTotalsService reports;
    public DailyCashTotalsController(DailyCashTotalsService reports){this.reports=reports;}
    @GetMapping public ResponseEntity<DailyCashTotalsService.Report> report(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@RequestParam String month){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(actor.getUserAccountId(),companyId,month));}
}
