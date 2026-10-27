package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentityAttemptGuardTest {

    private final IdentityAttemptGuard guard = new IdentityAttemptGuard(
            Clock.systemUTC(), 100, 10, 1, 10, 1, 1, 1, Duration.ofMinutes(15));

    @Test
    void shouldNormalizeEmailAndLimitLoginPairsWithoutLockingTheAccountGlobally() {
        guard.requireLogin("synthetic-address-a", " Person@Example.Invalid ");
        assertThatThrownBy(() -> guard.requireLogin("synthetic-address-a", "person@example.invalid"))
                .isInstanceOf(TooManyIdentityAttemptsException.class);
        assertThatCode(() -> guard.requireLogin("synthetic-address-b", "person@example.invalid"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldLimitResetDeliveriesAcrossAddressesWithoutExposingAccountExistence() {
        assertThat(guard.allowPasswordReset("synthetic-address-a", "person@example.invalid")).isTrue();
        assertThat(guard.allowPasswordReset("synthetic-address-b", "PERSON@example.invalid")).isFalse();
        assertThat(guard.allowPasswordReset("synthetic-address-b", "another@example.invalid")).isTrue();
    }

    @Test
    void shouldLimitCompletionAttempts() {
        guard.requireCompletion("synthetic-address");
        assertThatThrownBy(() -> guard.requireCompletion("synthetic-address"))
                .isInstanceOf(TooManyIdentityAttemptsException.class);
    }

    @Test
    void shouldLimitInvitationsPerActorWithoutConsumingPublicCompletionLimits() {
        var actor = java.util.UUID.randomUUID();
        guard.requireInvitation(actor);
        assertThatThrownBy(() -> guard.requireInvitation(actor)).isInstanceOf(TooManyIdentityAttemptsException.class);
        assertThatCode(() -> guard.requireInvitation(java.util.UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireCompletion("synthetic-address")).doesNotThrowAnyException();
    }
}
