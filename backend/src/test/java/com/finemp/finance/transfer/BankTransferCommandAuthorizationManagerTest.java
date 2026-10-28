package com.finemp.finance.transfer;

import com.finemp.access.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankTransferCommandAuthorizationManagerTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID();
    @Test void bothOptInsMustBeEnabledBeforeReadingPermissions() {
        for(var flags:new boolean[][]{{false,false},{true,false},{false,true}})assertThat(new BankTransferCommandAuthorizationManager(permissions,flags[0],flags[1])
                .authorize(this::authentication,context("POST",company.toString())).isGranted()).isFalse();verifyNoInteractions(permissions);
    }
    @Test void freshBankManageMustBeCheckedAndServerMarkerClearedOnRevocation() {
        var manager=new BankTransferCommandAuthorizationManager(permissions,true,true);var context=context("POST",company.toString());
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true,false);
        assertThat(manager.authorize(this::authentication,context).isGranted()).isTrue();assertThat(context.getRequest().getAttribute(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
        assertThat(manager.authorize(this::authentication,context).isGranted()).isFalse();assertThat(context.getRequest().getAttribute(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE)).isNull();
    }
    @Test void deleteAuthorizationMustNotMarkItsUnusedBodyForParsing() {
        var manager=new BankTransferCommandAuthorizationManager(permissions,true,true);when(permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true);
        var context=context("DELETE",company.toString());context.getRequest().setAttribute(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE,Boolean.TRUE);
        assertThat(manager.authorize(this::authentication,context).isGranted()).isTrue();assertThat(context.getRequest().getAttribute(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE)).isNull();
    }
    @Test void anonymousUnexpectedPrincipalAndInvalidCompanyMustFailBeforeDataAccess() {
        var manager=new BankTransferCommandAuthorizationManager(permissions,true,true);
        assertThat(manager.authorize(()->null,context("POST",company.toString())).isGranted()).isFalse();
        assertThat(manager.authorize(()->UsernamePasswordAuthenticationToken.authenticated("unexpected","",List.of()),context("POST",company.toString())).isGranted()).isFalse();
        assertThat(manager.authorize(this::authentication,context("POST","invalid")).isGranted()).isFalse();verifyNoInteractions(permissions);
    }
    UsernamePasswordAuthenticationToken authentication() {var principal=mock(UserAccountPrincipal.class);when(principal.getUserAccountId()).thenReturn(actor);return UsernamePasswordAuthenticationToken.authenticated(principal,"",List.of());}
    RequestAuthorizationContext context(String method,String company) {return new RequestAuthorizationContext(new MockHttpServletRequest(method,"/unused"),Map.of("companyId",company));}
}
