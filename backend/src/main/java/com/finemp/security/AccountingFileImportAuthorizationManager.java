package com.finemp.security;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public final class AccountingFileImportAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {
    private final AccountingSourceRepository sources;
    private final CompanyPermissionAuthorizationService permissions;

    public AccountingFileImportAuthorizationManager(
            AccountingSourceRepository sources,
            CompanyPermissionAuthorizationService permissions) {
        this.sources = sources;
        this.permissions = permissions;
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserAccountPrincipal principal)) {
            return new AuthorizationDecision(false);
        }

        String value = context.getVariables().get("accountingSourceId");
        if (value == null) {
            return new AuthorizationDecision(false);
        }

        UUID sourceId;
        try {
            sourceId = UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return new AuthorizationDecision(false);
        }

        boolean allowed = sources.findForReadingById(sourceId)
                .filter(source -> source.getType() == AccountingSourceType.FILE_IMPORT)
                .map(source -> permissions.hasFreshPermission(
                        principal.getUserAccountId(),
                        source.getCompany().getId(),
                        CompanyPermission.ACCOUNTING_IMPORT_MANAGE))
                .orElse(false);

        return new AuthorizationDecision(allowed);
    }
}
