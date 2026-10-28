package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Route authorization precedes bounded body consumption; the service rechecks after locking. */
@Component
public class EntryCommandAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final CompanyPermissionAuthorizationService permissions;
    private final boolean enabled;
    public EntryCommandAuthorizationManager(CompanyPermissionAuthorizationService permissions,
            @Value("${finemp.finance.entries.enabled:false}") boolean entriesEnabled,
            @Value("${finemp.finance.entries.commands.enabled:false}") boolean commandsEnabled) {
        this.permissions=permissions;this.enabled=entriesEnabled && commandsEnabled;
    }
    public boolean isEnabled() {return enabled;}
    @Override public AuthorizationDecision authorize(Supplier<? extends Authentication> supplier,RequestAuthorizationContext context) {
        if(!enabled)return new AuthorizationDecision(false);
        var auth=supplier.get();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserAccountPrincipal actor))return new AuthorizationDecision(false);
        String company=context.getVariables().get("companyId");if(company==null)return new AuthorizationDecision(false);
        try {return new AuthorizationDecision(permissions.hasFreshPermission(actor.getUserAccountId(),UUID.fromString(company),CompanyPermission.FINANCIAL_ENTRY_MANAGE));}
        catch(IllegalArgumentException invalid) {return new AuthorizationDecision(false);}
    }
}
