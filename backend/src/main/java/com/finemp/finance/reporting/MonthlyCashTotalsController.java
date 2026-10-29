package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/monthly-cash-totals")
@ConditionalOnProperty(prefix="finemp.finance.monthly-cash",name="enabled",havingValue="true")
public class MonthlyCashTotalsController {
    private final MonthlyCashTotalsService reports;
    public MonthlyCashTotalsController(MonthlyCashTotalsService reports){this.reports=reports;}
    @GetMapping public ResponseEntity<MonthlyCashTotalsService.Report> report(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@RequestParam int year){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(actor.getUserAccountId(),companyId,year));}
}
