package com.finemp.connector;

import static org.assertj.core.api.Assertions.*;
import com.finemp.identity.OpaqueAccountToken;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorCredentialTest {
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    @Test void shouldExpireAtTheExactDeadlineAndNeverAuthenticateBeforeCreationOrAfterRevocation() {
        var credential = create();
        assertThat(credential.isUsableAt(now.minusNanos(1))).isFalse();
        assertThat(credential.isUsableAt(now)).isTrue();
        assertThat(credential.isUsableAt(now.plusSeconds(10))).isFalse();
        assertThat(credential.revoke(now)).isTrue(); assertThat(credential.revoke(now.plusSeconds(1))).isFalse();
        assertThat(credential.isUsableAt(now.plusSeconds(1))).isFalse();
    }
    @Test void shouldNeverResurrectARevokedCredentialThroughRotation() {
        var credential = create(); credential.revoke(now);
        assertThatThrownBy(() -> credential.rotate(OpaqueAccountToken.generate().hash(), UUID.randomUUID(), now, now.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldKeepSecretValuesOutOfDiagnosticStringsAndMetadata() {
        var token = OpaqueAccountToken.generate(); var credential = create();
        var response = new ConnectorSecretResponse(ConnectorCredentialResponse.from(credential), token.rawValue());
        assertThat(response.toString()).doesNotContain(token.rawValue());
        assertThat(credential.toString()).contains("redacted").doesNotContain(token.hash());
        assertThat(ConnectorCredentialResponse.from(credential).toString()).doesNotContain(token.hash());
    }
    @Test void shouldRejectInvalidHashesLabelsAndNonPositiveLifetime() {
        assertThatThrownBy(() -> new ConnectorCredential(UUID.randomUUID(), UUID.randomUUID(), "  ", "a".repeat(64), UUID.randomUUID(), now, now.plusSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectorCredential(UUID.randomUUID(), UUID.randomUUID(), "Synthetic", "raw-secret", UUID.randomUUID(), now, now.plusSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create().rotate("a".repeat(64), UUID.randomUUID(), now, now)).isInstanceOf(IllegalArgumentException.class);
    }
    private ConnectorCredential create() { return new ConnectorCredential(UUID.randomUUID(), UUID.randomUUID(), "Synthetic Connector", OpaqueAccountToken.generate().hash(), UUID.randomUUID(), now, now.plusSeconds(10)); }
}
