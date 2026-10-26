package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.access.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountActionTokenTest {

    @Test
    void shouldExpireAtTheExactExpirationInstant() {
        Instant expiry = Instant.parse("2026-01-02T00:00:00Z");
        AccountActionToken token = new AccountActionToken(
                new UserAccount("Synthetic", "synthetic@example.invalid", "hash"),
                AccountActionPurpose.PASSWORD_RESET,
                "a".repeat(64),
                expiry.minusSeconds(60),
                expiry);

        assertThat(token.isUsable(AccountActionPurpose.PASSWORD_RESET, expiry.minusNanos(1))).isTrue();
        assertThat(token.isUsable(AccountActionPurpose.PASSWORD_RESET, expiry)).isFalse();
        assertThat(token.isUsable(AccountActionPurpose.INVITATION, expiry.minusSeconds(1))).isFalse();
    }

    @Test
    void shouldNotReuseAConsumedToken() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        AccountActionToken token = new AccountActionToken(
                new UserAccount("Synthetic", "synthetic@example.invalid", "hash"),
                AccountActionPurpose.INVITATION,
                "b".repeat(64),
                now,
                now.plusSeconds(60));

        token.consume(now.plusSeconds(1));

        assertThat(token.isUsable(AccountActionPurpose.INVITATION, now.plusSeconds(2))).isFalse();
    }

    @Test
    void shouldHashOpaqueTokenAndRedactItInDiagnostics() {
        OpaqueAccountToken token = OpaqueAccountToken.generate();

        assertThat(token.rawValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(token.hash()).hasSize(64).isNotEqualTo(token.rawValue());
        assertThat(OpaqueAccountToken.hash(token.rawValue())).isEqualTo(token.hash());
        assertThat(token.toString()).doesNotContain(token.rawValue());
    }

    @Test
    void shouldEnforceTheReferencePasswordPolicyAndEncoderByteLimit() {
        assertThat(AccountPasswordPolicy.isValid("example123")).isTrue();
        assertThat(AccountPasswordPolicy.isValid("no-digits")).isFalse();
        assertThat(AccountPasswordPolicy.isValid("12345678")).isFalse();
        assertThat(AccountPasswordPolicy.isValid("abc123")).isFalse();
        assertThat(AccountPasswordPolicy.isValid("á".repeat(36) + "a1")).isFalse();
    }
}
