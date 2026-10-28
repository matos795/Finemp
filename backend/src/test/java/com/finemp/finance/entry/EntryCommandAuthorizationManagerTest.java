package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntryCommandAuthorizationManagerTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID();
    @Test void bothExplicitOptInsAreRequiredBeforeEvenCheckingPermission() {
        for(var flags:new boolean[][]{{false,false},{true,false},{false,true}}) {
            var manager=new EntryCommandAuthorizationManager(permissions,flags[0],flags[1]);
            assertThat(manager.authorize(this::authentication,context(company.toString())).isGranted()).isFalse();
        }
        verifyNoInteractions(permissions);
    }
    @Test void authorizationMustUseFreshManagePermissionForTheRouteCompanyEveryTime() {
        var manager=new EntryCommandAuthorizationManager(permissions,true,true);
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true,false);
        assertThat(manager.authorize(this::authentication,context(company.toString())).isGranted()).isTrue();
        assertThat(manager.authorize(this::authentication,context(company.toString())).isGranted()).isFalse();
        verify(permissions,times(2)).hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
    }
    @Test void unauthenticatedUnexpectedPrincipalAndMalformedRouteMustFailBeforeDataAccess() {
        var manager=new EntryCommandAuthorizationManager(permissions,true,true);
        assertThat(manager.authorize(()->null,context(company.toString())).isGranted()).isFalse();
        assertThat(manager.authorize(()->UsernamePasswordAuthenticationToken.unauthenticated("user",""),context(company.toString())).isGranted()).isFalse();
        assertThat(manager.authorize(()->UsernamePasswordAuthenticationToken.authenticated("unexpected","",List.of()),context(company.toString())).isGranted()).isFalse();
        assertThat(manager.authorize(this::authentication,context("invalid")).isGranted()).isFalse();verifyNoInteractions(permissions);
    }
    private UsernamePasswordAuthenticationToken authentication() {var principal=mock(UserAccountPrincipal.class);when(principal.getUserAccountId()).thenReturn(actor);return UsernamePasswordAuthenticationToken.authenticated(principal,"",List.of());}
    private RequestAuthorizationContext context(String company) {return new RequestAuthorizationContext(new MockHttpServletRequest(),Map.of("companyId",company));}
}
