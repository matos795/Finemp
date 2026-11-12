package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/account-balances")
@ConditionalOnProperty(prefix="finemp.finance.account-balances",name="enabled",havingValue="true")
public class BankAccountBalanceReadController {
    private final BankAccountBalanceReadService reports;

    public BankAccountBalanceReadController(BankAccountBalanceReadService reports) {
        this.reports=reports;
    }

    @GetMapping
    public ResponseEntity<BankAccountBalanceReadService.Report> report(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reports.report(actor.getUserAccountId(),companyId));
    }
}
