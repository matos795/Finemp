package com.finemp.administration.firmaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.finemp.access.*;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.audit.AccessAuditRecorder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FirmAccessAdministrationStateTest {
    @Test
    void shouldReplaceDefensivelyWithoutChangingGlobalIdentityOrAnotherFirm() {
        var user = new UserAccount("Synthetic", "synthetic@example.invalid", "hash");
        var first = new AccountingFirmAccessGrant(user, new AccountingFirm("First", true),
                Set.of(AccountingFirmPermission.CONNECTOR_MANAGE));
        var other = new AccountingFirmAccessGrant(user, new AccountingFirm("Other", true),
                Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE));
        var requested = new java.util.HashSet<>(Set.of(AccountingFirmPermission.HIERARCHY_MANAGE));
        first.replacePermissions(requested);
        requested.clear();
        first.replacePermissions(first.getPermissions());
        assertThat(first.getPermissions()).containsExactly(AccountingFirmPermission.HIERARCHY_MANAGE);
        assertThat(other.getPermissions()).containsExactly(AccountingFirmPermission.USER_ACCESS_MANAGE);
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getPlatformPermissions()).isEmpty();
    }

    @Test
    void shouldNeverDeleteFirmGrantOrAuditSuccessWhileCompanyAccessRemains() {
        var grants = mock(AccountingFirmAccessGrantRepository.class);
        var firms = mock(AccountingFirmRepository.class);
        var users = mock(UserAccountRepository.class);
        var companyGrants = mock(CompanyAccessGrantRepository.class);
        var platform = mock(PlatformPermissionAuthorizationService.class);
        var firmAuthorization = mock(AccountingFirmPermissionAuthorizationService.class);
        var audit = mock(AccessAuditRecorder.class);
        var actorId = UUID.randomUUID();
        var targetId = UUID.randomUUID();
        var firmId = UUID.randomUUID();
        var user = new UserAccount("Synthetic", "synthetic@example.invalid", "hash");
        var firm = new AccountingFirm("Synthetic", true);
        when(platform.hasPermission(actorId, PlatformPermission.PLATFORM_USER_MANAGE)).thenReturn(true);
        when(firms.findById(firmId)).thenReturn(Optional.of(firm));
        when(users.findLockedById(targetId)).thenReturn(Optional.of(user));
        when(grants.findByUserAccountIdAndAccountingFirmId(targetId, firmId))
                .thenReturn(Optional.of(new AccountingFirmAccessGrant(user, firm, Set.of())));
        when(companyGrants.existsWithinAccountingFirm(targetId, firmId)).thenReturn(true);
        var service = new FirmAccessAdministrationService(grants, firms, users, companyGrants,
                platform, firmAuthorization, audit);
        assertThatThrownBy(() -> service.revoke(actorId, firmId, targetId)).isInstanceOf(FirmAccessConflictException.class);
        verify(grants, never()).delete(any());
        verifyNoInteractions(audit);
    }
}
