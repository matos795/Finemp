package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/presentation-snapshot")
@ConditionalOnProperty(name={"finemp.finance.presentation.enabled","finemp.finance.aging.enabled","finemp.finance.daily-cash.enabled","finemp.finance.monthly-cash.enabled"},havingValue="true")
public class FinancialPresentationController {
    private final FinancialPresentationSnapshotService snapshots;
    public FinancialPresentationController(FinancialPresentationSnapshotService snapshots){this.snapshots=snapshots;}
    @GetMapping public ResponseEntity<FinancialPresentationSnapshotService.Snapshot> snapshot(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam String month,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,@RequestParam OpenTitlesAgingService.Scope scope){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.snapshot(actor.getUserAccountId(),companyId,month,asOf,scope));
    }
}
