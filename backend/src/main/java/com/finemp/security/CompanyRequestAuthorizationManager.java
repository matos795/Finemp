package com.finemp.security;

import com.finemp.access.CompanyAccessAuthorizationService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class CompanyRequestAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private final CompanyAccessAuthorizationService authorizationService;

    public CompanyRequestAuthorizationManager(
            CompanyAccessAuthorizationService authorizationService) {

        this.authorizationService = authorizationService;
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {

        Authentication authentication = authenticationSupplier.get();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserAccountPrincipal principal)) {

            return new AuthorizationDecision(false);
        }

        String companyIdValue = context.getVariables().get("companyId");

        if (companyIdValue == null) {
            return new AuthorizationDecision(false);
        }

        try {
            UUID companyId = UUID.fromString(companyIdValue);

            boolean granted = authorizationService.hasAccess(
                    principal.getUserAccountId(),
                    companyId);

            return new AuthorizationDecision(granted);

        } catch (IllegalArgumentException exception) {
            return new AuthorizationDecision(false);
        }
    }
}