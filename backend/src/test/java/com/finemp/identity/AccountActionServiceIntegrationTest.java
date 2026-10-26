package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@RecordApplicationEvents
class AccountActionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private AccountActionService service;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AccountActionTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void shouldActivateInvitationOnceAndPersistOnlyTokenHash() {
        service.inviteNewUser("Synthetic User", "INVITED@example.invalid");
        AccountActionRequested delivery = lastDelivery();
        String raw = delivery.token().rawValue();
        UserAccount invited = userAccountRepository.findByEmailIgnoreCase("invited@example.invalid").orElseThrow();
        AccountActionTokenReference tokenReference = tokenRepository
                .findReferenceByTokenHash(OpaqueAccountToken.hash(raw)).orElseThrow();

        assertThat(invited.isActive()).isFalse();
        assertThat(invited.isInvitationPending()).isTrue();
        assertThat(tokenRepository.findById(tokenReference.id()).orElseThrow().getTokenHash())
                .isEqualTo(OpaqueAccountToken.hash(raw)).isNotEqualTo(raw);
        assertThat(delivery.toString()).doesNotContain(raw).doesNotContain(delivery.email());

        service.complete(raw, AccountActionPurpose.INVITATION, "newPassword123");
        entityManager.flush();
        entityManager.clear();
        UserAccount activated = userAccountRepository.findById(invited.getId()).orElseThrow();

        assertThat(activated.isActive()).isTrue();
        assertThat(activated.isInvitationPending()).isFalse();
        assertThat(activated.getCredentialsVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches("newPassword123", activated.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> service.complete(raw, AccountActionPurpose.INVITATION, "otherPassword123"))
                .isInstanceOf(InvalidAccountActionException.class);
    }

    @Test
    void shouldExpireInvitationAtTheBoundary() {
        service.inviteNewUser("Synthetic User", "expired@example.invalid");
        AccountActionRequested delivery = lastDelivery();
        when(clock.instant()).thenReturn(delivery.expiresAt());

        assertThatThrownBy(() -> service.complete(
                delivery.token().rawValue(), AccountActionPurpose.INVITATION, "newPassword123"))
                .isInstanceOf(InvalidAccountActionException.class);
    }

    @Test
    void shouldInvalidateOlderResetAndChangePasswordOnlyOnce() {
        UserAccount userAccount = userAccountRepository.saveAndFlush(new UserAccount(
                "Synthetic User", "reset@example.invalid", passwordEncoder.encode("oldPassword123")));

        service.requestPasswordReset(userAccount.getEmail());
        String oldToken = lastDelivery().token().rawValue();
        service.requestPasswordReset(userAccount.getEmail());
        String currentToken = lastDelivery().token().rawValue();

        assertThatThrownBy(() -> service.complete(oldToken, AccountActionPurpose.PASSWORD_RESET, "newPassword123"))
                .isInstanceOf(InvalidAccountActionException.class);

        service.complete(currentToken, AccountActionPurpose.PASSWORD_RESET, "newPassword123");
        entityManager.flush();
        entityManager.clear();
        UserAccount updated = userAccountRepository.findById(userAccount.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newPassword123", updated.getPasswordHash())).isTrue();
        assertThat(updated.getCredentialsVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.complete(currentToken, AccountActionPurpose.PASSWORD_RESET, "anotherPassword123"))
                .isInstanceOf(InvalidAccountActionException.class);
    }

    @Test
    void shouldNotIssueResetForUnknownOrInactiveAccount() {
        UserAccount inactive = new UserAccount("Synthetic", "inactive@example.invalid", "hash");
        inactive.deactivate();
        userAccountRepository.saveAndFlush(inactive);

        service.requestPasswordReset("unknown@example.invalid");
        service.requestPasswordReset(inactive.getEmail());

        assertThat(events.stream(AccountActionRequested.class)).isEmpty();
    }

    @Test
    void shouldNotReplaceAnExistingActiveUsersPasswordOrActivationState() {
        UserAccount active = userAccountRepository.saveAndFlush(new UserAccount(
                "Existing Synthetic", "existing@example.invalid", passwordEncoder.encode("existingPassword123")));

        service.inviteNewUser("Another Name", active.getEmail());

        assertThat(events.stream(AccountActionRequested.class)).isEmpty();
        assertThat(active.isActive()).isTrue();
        assertThat(active.getName()).isEqualTo("Existing Synthetic");
        assertThat(passwordEncoder.matches("existingPassword123", active.getPasswordHash())).isTrue();
    }

    @Test
    void shouldRejectWrongPurposeWithoutAcceptingTheLink() {
        service.inviteNewUser("Synthetic", "purpose@example.invalid");
        String token = lastDelivery().token().rawValue();

        assertThatThrownBy(() -> service.complete(token, AccountActionPurpose.PASSWORD_RESET, "newPassword123"))
                .isInstanceOf(InvalidAccountActionException.class);
    }

    private AccountActionRequested lastDelivery() {
        return events.stream(AccountActionRequested.class).reduce((first, last) -> last).orElseThrow();
    }

    @Test
    void shouldRejectResetIssuedBeforeAnotherCredentialChange() {
        UserAccount userAccount = userAccountRepository.saveAndFlush(new UserAccount(
                "Synthetic", "version@example.invalid", passwordEncoder.encode("oldPassword123")));
        service.requestPasswordReset(userAccount.getEmail());
        String token = lastDelivery().token().rawValue();

        userAccount.changePassword(passwordEncoder.encode("otherPassword123"));
        userAccountRepository.saveAndFlush(userAccount);

        assertThatThrownBy(() -> service.complete(token, AccountActionPurpose.PASSWORD_RESET, "replayedPassword123"))
                .isInstanceOf(InvalidAccountActionException.class);
    }
}
