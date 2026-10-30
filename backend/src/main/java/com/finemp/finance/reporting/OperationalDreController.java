package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/operational-dre")
@ConditionalOnProperty(prefix="finemp.finance.operational-dre",name="enabled",havingValue="true")
public class OperationalDreController {
    private final OperationalDreMonthlyReadService reports;
    public OperationalDreController(OperationalDreMonthlyReadService reports){this.reports=reports;}
    @GetMapping public ResponseEntity<OperationalDreMonthlyReadService.Report> report(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@RequestParam String month){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(actor.getUserAccountId(),companyId,month));
    }
}
