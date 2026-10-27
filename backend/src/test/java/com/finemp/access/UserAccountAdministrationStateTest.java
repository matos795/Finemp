package com.finemp.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAccountAdministrationStateTest {

    @Test
    void shouldReactivateAnEstablishedIdentityWithoutRestoringOldCredentialVersions() {
        var user = new UserAccount("Synthetic", "state@example.invalid", "existing-hash");
        user.deactivate();
        long disabledVersion = user.getCredentialsVersion();

        user.reactivate();

        assertThat(user.isActive()).isTrue();
        assertThat(user.getCredentialsVersion()).isGreaterThan(disabledVersion);
        assertThat(user.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void shouldNeverActivateAPendingOrRevokedInvitationMarker() {
        var user = UserAccount.invited("Synthetic Pending", "pending-state@example.invalid");
        assertThatThrownBy(user::reactivate).isInstanceOf(IllegalStateException.class);
        user.deactivate();
        assertThatThrownBy(user::reactivate).isInstanceOf(IllegalStateException.class);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void shouldReplaceOnlyExplicitPlatformPermissionsWithoutChangingCredentials() {
        var user = new UserAccount("Synthetic", "permissions-state@example.invalid", "existing-hash",
                Set.of(PlatformPermission.ACCOUNTING_FIRM_CREATE));

        user.replacePlatformPermissions(Set.of(PlatformPermission.ACCOUNTING_FIRM_VIEW));

        assertThat(user.getPlatformPermissions()).containsExactly(PlatformPermission.ACCOUNTING_FIRM_VIEW);
        assertThat(user.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(user.getCredentialsVersion()).isZero();
        assertThatThrownBy(() -> user.replacePlatformPermissions(null)).isInstanceOf(NullPointerException.class);
        assertThat(user.getPlatformPermissions()).containsExactly(PlatformPermission.ACCOUNTING_FIRM_VIEW);
    }
}
