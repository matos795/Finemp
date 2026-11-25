package com.finemp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class AccountingFileImportAuthorizationManagerTest {
    private final AccountingSourceRepository sources = mock(AccountingSourceRepository.class);
    private final CompanyPermissionAuthorizationService permissions =
            mock(CompanyPermissionAuthorizationService.class);
    private final AccountingFileImportAuthorizationManager manager =
            new AccountingFileImportAuthorizationManager(sources, permissions);

    @Test
    void shouldRequireFileImportSourceAndFreshCompanyPermission() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var company = mock(Company.class);
        UUID companyId = UUID.randomUUID();
        when(company.getId()).thenReturn(companyId);
        var source = mock(AccountingSource.class);
        when(source.getType()).thenReturn(AccountingSourceType.FILE_IMPORT);
        when(source.getCompany()).thenReturn(company);
        var principal = mock(UserAccountPrincipal.class);
        when(principal.getUserAccountId()).thenReturn(userId);
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.of(source));
        when(permissions.hasFreshPermission(
                userId,
                companyId,
                CompanyPermission.ACCOUNTING_IMPORT_MANAGE)).thenReturn(true);

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of());

        assertThat(manager.authorize(() -> authentication, context(sourceId.toString())).isGranted())
                .isTrue();
        verify(permissions).hasFreshPermission(
                userId,
                companyId,
                CompanyPermission.ACCOUNTING_IMPORT_MANAGE);
    }

    @Test
    void shouldRejectQuestorSourceEvenWhenTheUserHasImportPermission() {
        UUID sourceId = UUID.randomUUID();
        var source = mock(AccountingSource.class);
        when(source.getType()).thenReturn(AccountingSourceType.QUESTOR);
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.of(source));
        var principal = mock(UserAccountPrincipal.class);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of());

        assertThat(manager.authorize(() -> authentication, context(sourceId.toString())).isGranted())
                .isFalse();
        verifyNoInteractions(permissions);
    }

    @Test
    void shouldRejectUnknownOrInvalidSourceWithoutCheckingPermissions() {
        var principal = mock(UserAccountPrincipal.class);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of());

        assertThat(manager.authorize(() -> authentication, context("invalid")).isGranted()).isFalse();

        UUID sourceId = UUID.randomUUID();
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.empty());
        assertThat(manager.authorize(() -> authentication, context(sourceId.toString())).isGranted())
                .isFalse();

        verifyNoInteractions(permissions);
    }

    private RequestAuthorizationContext context(String sourceId) {
        return new RequestAuthorizationContext(
                new MockHttpServletRequest(),
                Map.of("accountingSourceId", sourceId));
    }


}
}