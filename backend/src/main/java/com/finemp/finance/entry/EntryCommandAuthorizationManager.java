package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Route authorization precedes bounded body consumption; the service rechecks after locking. */
public class EntryCommandAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    static final String AUTHORIZED_WRITER_ATTRIBUTE=EntryCommandAuthorizationManager.class.getName()+".AUTHORIZED_WRITER";
    private final CompanyPermissionAuthorizationService permissions;
    private final boolean enabled;
    public EntryCommandAuthorizationManager(CompanyPermissionAuthorizationService permissions,
            boolean entriesEnabled,boolean commandsEnabled) {
        this.permissions=permissions;this.enabled=entriesEnabled && commandsEnabled;
    }
    public boolean isEnabled() {return enabled;}
    @Override public AuthorizationDecision authorize(Supplier<? extends Authentication> supplier,RequestAuthorizationContext context) {
        context.getRequest().removeAttribute(AUTHORIZED_WRITER_ATTRIBUTE);
        if(!enabled)return new AuthorizationDecision(false);
        var auth=supplier.get();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserAccountPrincipal actor))return new AuthorizationDecision(false);
        String company=context.getVariables().get("companyId");if(company==null)return new AuthorizationDecision(false);
        try {
            boolean granted=permissions.hasFreshPermission(actor.getUserAccountId(),UUID.fromString(company),CompanyPermission.FINANCIAL_ENTRY_MANAGE);
            // This is a server-only request attribute, set only by successful normalized route authorization.
            if(granted)context.getRequest().setAttribute(AUTHORIZED_WRITER_ATTRIBUTE,Boolean.TRUE);
            return new AuthorizationDecision(granted);
        }
        catch(IllegalArgumentException invalid) {return new AuthorizationDecision(false);}
    }
}
