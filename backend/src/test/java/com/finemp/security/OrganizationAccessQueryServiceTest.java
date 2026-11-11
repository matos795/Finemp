package com.finemp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationAccessQueryServiceTest {

    @Test
    void shouldQueryOnlyTheRequestedIdentityAndSortAdministrativeBindings() {
        var users = mock(UserAccountRepository.class);
        var grants = mock(AccountingFirmAccessGrantRepository.class);
        UUID id = UUID.randomUUID();
        var actor = new UserAccount("Synthetic", "context-unit@example.invalid", "hash",
                Set.of(PlatformPermission.ACCOUNTING_FIRM_CREATE));
        when(users.findById(id)).thenReturn(Optional.of(actor));
        when(grants.findAllByUserAccountId(id)).thenReturn(List.of(
                new AccountingFirmAccessGrant(actor, new AccountingFirm("Zeta Synthetic", true), Set.of()),
                new AccountingFirmAccessGrant(actor, new AccountingFirm("Alpha Synthetic", false),
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE))));

        var response = new OrganizationAccessQueryService(users, grants, mock(com.finemp.accountingfirm.AccountingFirmRepository.class)).findForUser(id);

        assertThat(response.platformPermissions()).containsExactly(PlatformPermission.ACCOUNTING_FIRM_CREATE);
        assertThat(response.accountingFirms()).extracting(OrganizationAccessResponse.AccountingFirmAccess::name)
                .containsExactly("Alpha Synthetic", "Zeta Synthetic");
        assertThat(response.accountingFirms().getFirst().active()).isFalse();
        verify(grants).findAllByUserAccountId(id);
    }
}
