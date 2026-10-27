package com.finemp.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationAccessController {

    private final OrganizationAccessQueryService query;

    public OrganizationAccessController(OrganizationAccessQueryService query) { this.query = query; }

    @GetMapping("/api/auth/organization-access")
    public OrganizationAccessResponse current(@AuthenticationPrincipal UserAccountPrincipal principal) {
        return query.findForUser(principal.getUserAccountId());
    }
}
