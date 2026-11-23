package com.finemp.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountingPortfolioController {

    private final AccountingPortfolioQueryService query;

    public AccountingPortfolioController(AccountingPortfolioQueryService query) {
        this.query = query;
    }

    /** Identidade somente pela sessão; recalculado a cada chamada (revogação vale na hora). */
    @GetMapping("/api/auth/accounting-portfolio")
    public ResponseEntity<AccountingPortfolioResponse> current(@AuthenticationPrincipal UserAccountPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(query.findForUser(principal.getUserAccountId()));
    }
}
