package com.finemp.finance.reporting;

import com.finemp.finance.category.FinancialDirection;
import com.finemp.security.UserAccountPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/open-titles/aging")
@ConditionalOnProperty(prefix="finemp.finance.aging",name="enabled",havingValue="true")
public class OpenTitlesAgingController {
    private final OpenTitlesAgingService reports;
    public OpenTitlesAgingController(OpenTitlesAgingService reports){this.reports=reports;}
    @GetMapping public ResponseEntity<OpenTitlesAgingService.Report> report(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam FinancialDirection direction,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,@RequestParam(defaultValue="ALL") OpenTitlesAgingService.Scope scope){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(actor.getUserAccountId(),companyId,direction,asOf,scope));
    }
}
