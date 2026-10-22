package com.finemp.security;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CompanyPermissionAuthorizationManagerFactory {

    private final CompanyPermissionAuthorizationService authorizationService;

    public CompanyPermissionAuthorizationManagerFactory(
            CompanyPermissionAuthorizationService authorizationService) {

        this.authorizationService = authorizationService;
    }

    public AuthorizationManager<RequestAuthorizationContext> forPermission(
            CompanyPermission permission) {

        return (authenticationSupplier, context) -> {

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

                boolean granted = authorizationService.hasPermission(
                        principal.getUserAccountId(),
                        companyId,
                        permission);

                return new AuthorizationDecision(granted);

            } catch (IllegalArgumentException exception) {
                return new AuthorizationDecision(false);
            }
        };
    }
}