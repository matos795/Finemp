package com.finemp.finance.transfer;

import com.finemp.access.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Normalized route decision precedes POST body consumption. The service rechecks after locking. */
public class BankTransferCommandAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    static final String BODY_ATTRIBUTE=BankTransferCommandAuthorizationManager.class.getName()+".AUTHORIZED_POST_BODY";
    private final CompanyPermissionAuthorizationService permissions;
    private final boolean enabled;
    public BankTransferCommandAuthorizationManager(CompanyPermissionAuthorizationService permissions,boolean transfersEnabled,boolean commandsEnabled) {
        this.permissions=permissions;this.enabled=transfersEnabled && commandsEnabled;
    }
    public boolean isEnabled() {return enabled;}
    @Override public AuthorizationDecision authorize(Supplier<? extends Authentication> supplier,RequestAuthorizationContext context) {
        var request=context.getRequest();request.removeAttribute(BODY_ATTRIBUTE);if(!enabled)return new AuthorizationDecision(false);
        var auth=supplier.get();if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserAccountPrincipal actor))return new AuthorizationDecision(false);
        var company=context.getVariables().get("companyId");if(company==null)return new AuthorizationDecision(false);
        try {
            boolean granted=permissions.hasFreshPermission(actor.getUserAccountId(),UUID.fromString(company),CompanyPermission.BANK_ACCOUNT_MANAGE);
            if(granted && "POST".equals(request.getMethod()))request.setAttribute(BODY_ATTRIBUTE,Boolean.TRUE);
            return new AuthorizationDecision(granted);
        }catch(IllegalArgumentException invalid) {return new AuthorizationDecision(false);}
    }
}
