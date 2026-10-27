package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import({TestcontainersConfiguration.class, IdentityAuditIntegrationTest.CaptureConfiguration.class})
@Transactional
class IdentityAuditIntegrationTest {

    @Autowired private AccountActionService actions;
    @Autowired private FirmInvitationService invitations;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Capture capture;

    @BeforeEach
    void clearCapture() { capture.events.clear(); }

    @Test
    void shouldAuditInvitationAndActivationWithoutTokenPasswordOrEmail() {
        UUID actor = UUID.randomUUID();
        String email = "audit-invitation-" + UUID.randomUUID() + "@example.invalid";
        actions.inviteNewUser("Synthetic Audit", email, actor);
        var event = capture.events.remove();
        UUID userId = users.findIdByEmailIgnoreCase(email).orElseThrow();
        actions.complete(event.token().rawValue(), AccountActionPurpose.INVITATION, "syntheticPassword123");
        entityManager.flush();

        var events = jdbc.queryForList("SELECT * FROM access_audit_events WHERE subject_user_account_id = ?", userId);
        assertThat(events).extracting(row -> row.get("action"))
                .containsExactlyInAnyOrder("INVITATION_ISSUED", "ACCOUNT_ACTIVATED");
        assertThat(events.stream().filter(row -> row.get("action").equals("INVITATION_ISSUED")).findFirst().orElseThrow())
                .containsEntry("actor_user_account_id", actor);
        assertThat(events.toString()).doesNotContain(event.token().rawValue(), "syntheticPassword123", email);
    }

    @Test
    void shouldAuditPublicResetWithoutFabricatingAnAdministrativeActorAndNotAuditReplaySuccess() {
        var user = users.saveAndFlush(new UserAccount("Synthetic Reset Audit", "audit-reset-" + UUID.randomUUID()
                + "@example.invalid", "original-hash"));
        actions.requestPasswordReset(user.getEmail());
        var event = capture.events.remove();
        actions.complete(event.token().rawValue(), AccountActionPurpose.PASSWORD_RESET, "syntheticPassword123");
        assertThatThrownBy(() -> actions.complete(event.token().rawValue(), AccountActionPurpose.PASSWORD_RESET,
                "syntheticPassword456")).isInstanceOf(InvalidAccountActionException.class);
        entityManager.flush();

        var events = jdbc.queryForList("SELECT * FROM access_audit_events WHERE subject_user_account_id = ?", user.getId());
        assertThat(events).hasSize(2);
        assertThat(events.stream().filter(row -> row.get("action").equals("PASSWORD_RESET_ISSUED")).findFirst().orElseThrow())
                .containsEntry("actor_user_account_id", null);
        assertThat(events).extracting(row -> row.get("action"))
                .containsExactlyInAnyOrder("PASSWORD_RESET_ISSUED", "PASSWORD_CHANGED");
    }

    @Test
    void shouldAuditOnlyTheExplicitFirmGrantForAnAlreadyActiveIdentity() {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Audit Firm", true));
        var actor = users.saveAndFlush(new UserAccount("Synthetic Admin", "audit-admin-" + UUID.randomUUID()
                + "@example.invalid", "admin-hash"));
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        var target = users.saveAndFlush(new UserAccount("Original Synthetic", "audit-existing-" + UUID.randomUUID()
                + "@example.invalid", "original-hash"));

        invitations.invite(actor.getId(), firm.getId(), new InviteFirmUserRequest("Changed Name", target.getEmail(),
                Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));
        entityManager.flush();

        var events = jdbc.queryForList("SELECT * FROM access_audit_events WHERE subject_user_account_id = ?", target.getId());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).containsEntry("action", "FIRM_ACCESS_GRANTED")
                .containsEntry("actor_user_account_id", actor.getId()).containsEntry("accounting_firm_id", firm.getId());
        assertThat(jdbc.queryForList("SELECT permission FROM access_audit_event_permissions WHERE event_id = ?",
                String.class, events.getFirst().get("id"))).containsExactly("HIERARCHY_MANAGE");
        assertThat(capture.events).isEmpty();
        assertThat(target.getPasswordHash()).isEqualTo("original-hash");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {
        @Bean Capture identityAuditCapture() { return new Capture(); }
    }

    static class Capture {
        final LinkedBlockingQueue<AccountActionRequested> events = new LinkedBlockingQueue<>();
        @EventListener public void requested(AccountActionRequested event) { events.add(event); }
    }
}
