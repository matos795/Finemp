package com.finemp.sales.indicators;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/sales/indicators")
public class SalesIndicatorsController {

    private final SalesIndicatorsService service;

    public SalesIndicatorsController(
            SalesIndicatorsService service) {
        this.service = service;
    }

    @GetMapping
    public SalesIndicatorsResponse get(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId) {

        return service.get(
                actor.getUserAccountId(),
                companyId);
    }
}
