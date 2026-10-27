package com.finemp.security;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSourceRepository;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public class QuestorImportAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AccountingSourceRepository sources;
    private final CompanyPermissionAuthorizationService permissions;

    public QuestorImportAuthorizationManager(AccountingSourceRepository sources,
                                             CompanyPermissionAuthorizationService permissions) {
        this.sources = sources;
        this.permissions = permissions;
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authenticationSupplier,
                                           RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserAccountPrincipal principal)) {
            return new AuthorizationDecision(false);
        }
        String value = context.getVariables().get("accountingSourceId");
        if (value == null) return new AuthorizationDecision(false);
        UUID sourceId;
        try {
            sourceId = UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return new AuthorizationDecision(false);
        }
        boolean allowed = sources.findCompanyIdById(sourceId)
                .map(companyId -> permissions.hasPermission(principal.getUserAccountId(), companyId,
                        CompanyPermission.QUESTOR_SYNC)).orElse(false);
        return new AuthorizationDecision(allowed);
    }
}
