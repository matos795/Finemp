package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/cash-vs-dre")
@ConditionalOnProperty(
        name = {
                "finemp.finance.monthly-cash.enabled",
                "finemp.finance.operational-dre.enabled"
        },
        havingValue = "true")
public class CashVsOperationalDreController {

    private final CashVsOperationalDreService comparisons;

    public CashVsOperationalDreController(CashVsOperationalDreService comparisons) {
        this.comparisons = comparisons;
    }

    @GetMapping
    public ResponseEntity<CashVsOperationalDreService.Report> compare(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam String month) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(comparisons.compare(
                        actor.getUserAccountId(),
                        companyId,
                        month));
    }
}
