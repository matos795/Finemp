package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FirmInvitationServiceIntegrationTest {

    @Autowired private FirmInvitationService service;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;
    @Autowired private CompanyAccessGrantRepository companyGrants;
    @Autowired private AccountActionTokenRepository tokens;

    @Test
    void shouldInviteIntoOnlyTheAuthorizedFirmWithoutFinancialAccess() {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Firm A", true));
        var actor = actor(firm, AccountingFirmPermission.USER_ACCESS_MANAGE);
        String email = "firm-invite-" + UUID.randomUUID() + "@example.invalid";
        long companyGrantCount = companyGrants.count();

        service.invite(actor.getId(), firm.getId(), new InviteFirmUserRequest(
                "Synthetic Invited", email, Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        var invited = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(invited.isInvitationPending()).isTrue();
        assertThat(grants.findByUserAccountIdAndAccountingFirmId(invited.getId(), firm.getId()).orElseThrow()
                .getPermissions()).containsExactly(AccountingFirmPermission.HIERARCHY_MANAGE);
        assertThat(companyGrants.count()).isEqualTo(companyGrantCount);
    }

    @Test
    void shouldRejectAnotherFirmBeforeCreatingOrLookingUpTheInvitedIdentity() {
        var firmA = firms.saveAndFlush(new AccountingFirm("Synthetic Firm A", true));
        var firmB = firms.saveAndFlush(new AccountingFirm("Synthetic Firm B", true));
        var actor = actor(firmA, AccountingFirmPermission.USER_ACCESS_MANAGE);
        String email = "foreign-" + UUID.randomUUID() + "@example.invalid";

        assertThatThrownBy(() -> service.invite(actor.getId(), firmB.getId(),
                new InviteFirmUserRequest("Synthetic Foreign", email, Set.of())))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void shouldNotTreatHierarchyAdministrationAsUserAdministration() {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Hierarchy", true));
        var actor = actor(firm, AccountingFirmPermission.HIERARCHY_MANAGE);

        assertThatThrownBy(() -> service.invite(actor.getId(), firm.getId(),
                new InviteFirmUserRequest("Synthetic Denied", "denied@example.invalid", Set.of())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldPreserveActiveIdentityAndExistingGrantsWhenInvitedAgain() {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Existing", true));
        var actor = actor(firm, AccountingFirmPermission.USER_ACCESS_MANAGE);
        var existing = users.saveAndFlush(new UserAccount("Original Synthetic", "existing-" + UUID.randomUUID()
                + "@example.invalid", "original-hash"));
        grants.saveAndFlush(new AccountingFirmAccessGrant(existing, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        long tokenCount = tokens.count();

        service.invite(actor.getId(), firm.getId(), new InviteFirmUserRequest("Changed Name", existing.getEmail(),
                Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        assertThat(existing.getName()).isEqualTo("Original Synthetic");
        assertThat(existing.getPasswordHash()).isEqualTo("original-hash");
        assertThat(existing.isActive()).isTrue();
        assertThat(tokens.count()).isEqualTo(tokenCount);
        assertThat(grants.findByUserAccountIdAndAccountingFirmId(existing.getId(), firm.getId()).orElseThrow()
                .getPermissions()).containsExactlyInAnyOrder(
                        AccountingFirmPermission.CONNECTOR_MANAGE, AccountingFirmPermission.HIERARCHY_MANAGE);
    }

    private UserAccount actor(AccountingFirm firm, AccountingFirmPermission permission) {
        var actor = users.saveAndFlush(new UserAccount("Synthetic Admin", "admin-" + UUID.randomUUID()
                + "@example.invalid", "synthetic-hash"));
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(permission)));
        return actor;
    }
}
