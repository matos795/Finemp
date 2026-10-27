package com.finemp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSourceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class QuestorImportAuthorizationManagerTest {

    private final AccountingSourceRepository sources = mock(AccountingSourceRepository.class);
    private final CompanyPermissionAuthorizationService permissions = mock(CompanyPermissionAuthorizationService.class);
    private final QuestorImportAuthorizationManager manager = new QuestorImportAuthorizationManager(sources, permissions);

    @Test
    void shouldResolveTheSourceCompanyOnTheServerBeforeCheckingSyncPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var principal = mock(UserAccountPrincipal.class);
        when(principal.getUserAccountId()).thenReturn(userId);
        when(sources.findCompanyIdById(sourceId)).thenReturn(Optional.of(companyId));
        when(permissions.hasPermission(userId, companyId, CompanyPermission.QUESTOR_SYNC)).thenReturn(true);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());

        assertThat(manager.authorize(() -> authentication, context(sourceId.toString())).isGranted()).isTrue();
        verify(permissions).hasPermission(userId, companyId, CompanyPermission.QUESTOR_SYNC);
    }

    @Test
    void shouldRejectAnUnknownSourceWithoutCheckingAnotherCompanyOrImporting() {
        var principal = mock(UserAccountPrincipal.class);
        UUID sourceId = UUID.randomUUID();
        when(sources.findCompanyIdById(sourceId)).thenReturn(Optional.empty());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());

        assertThat(manager.authorize(() -> authentication, context(sourceId.toString())).isGranted()).isFalse();
        verifyNoInteractions(permissions);
    }

    @Test
    void shouldRejectInvalidIdentifiersBeforeLookingUpAnySource() {
        var principal = mock(UserAccountPrincipal.class);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());

        assertThat(manager.authorize(() -> authentication, context("invalid")).isGranted()).isFalse();
        verifyNoInteractions(sources, permissions);
    }

    @Test
    void shouldRejectAnonymousRequestsWithoutARepositoryLookup() {
        assertThat(manager.authorize(() -> null, context(UUID.randomUUID().toString())).isGranted()).isFalse();
        verifyNoInteractions(sources, permissions);
    }

    private RequestAuthorizationContext context(String sourceId) {
        return new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of("accountingSourceId", sourceId));
    }
}
