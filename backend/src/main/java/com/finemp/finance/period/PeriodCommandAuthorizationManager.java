package com.finemp.finance.period;

import com.finemp.access.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public class PeriodCommandAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    static final String BODY_ATTRIBUTE=PeriodCommandAuthorizationManager.class.getName()+".AUTHORIZED_POST_BODY";
    private final CompanyPermissionAuthorizationService permissions;private final CompanyPermission permission;private final boolean enabled;
    public PeriodCommandAuthorizationManager(CompanyPermissionAuthorizationService permissions,CompanyPermission permission,boolean periodsEnabled,boolean commandsEnabled) {
        if(permission!=CompanyPermission.FINANCIAL_PERIOD_CLOSE && permission!=CompanyPermission.FINANCIAL_PERIOD_REOPEN)throw new IllegalArgumentException("Permissão de período inválida.");
        this.permissions=permissions;this.permission=permission;this.enabled=periodsEnabled && commandsEnabled;
    }
    public boolean isEnabled() {return enabled;}
    @Override public AuthorizationDecision authorize(Supplier<? extends Authentication> supplier,RequestAuthorizationContext context) {
        var request=context.getRequest();request.removeAttribute(BODY_ATTRIBUTE);if(!enabled)return new AuthorizationDecision(false);
        var auth=supplier.get();if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserAccountPrincipal actor))return new AuthorizationDecision(false);
        try {
            var company=context.getVariables().get("companyId");if(company==null)return new AuthorizationDecision(false);
            boolean granted=permissions.hasFreshPermission(actor.getUserAccountId(),UUID.fromString(company),permission);
            if(granted && "POST".equals(request.getMethod()))request.setAttribute(BODY_ATTRIBUTE,Boolean.TRUE);
            return new AuthorizationDecision(granted);
        }catch(IllegalArgumentException invalid) {return new AuthorizationDecision(false);}
    }
}
