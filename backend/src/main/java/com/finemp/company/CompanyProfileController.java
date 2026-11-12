package com.finemp.company;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/profile")
public class CompanyProfileController {

    private final CompanyProfileQueryService service;

    public CompanyProfileController(CompanyProfileQueryService service) {
        this.service = service;
    }

    @GetMapping
    public CompanyProfileResponse get(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID companyId) {

        return service.get(principal.getUserAccountId(), companyId);
    }
}
