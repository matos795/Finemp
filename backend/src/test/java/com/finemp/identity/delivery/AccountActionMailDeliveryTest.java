package com.finemp.identity.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.finemp.identity.AccountActionPurpose;
import com.finemp.identity.AccountActionRequested;
import com.finemp.identity.OpaqueAccountToken;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountActionMailDeliveryTest {

    @Mock
    private AccountActionMailSender sender;

    @Test
    void shouldPutTheOpaqueTokenOnlyInTheFrontendFragment() {
        OpaqueAccountToken token = OpaqueAccountToken.generate();
        AccountActionMailDelivery delivery = new AccountActionMailDelivery(
                sender, URI.create("https://app.example.invalid/finemp/"), true);

        delivery.requested(new AccountActionRequested(
                "synthetic@example.invalid", AccountActionPurpose.INVITATION,
                token, Instant.parse("2026-01-02T00:00:00Z")));

        var argument = ArgumentCaptor.forClass(AccountActionMail.class);
        verify(sender).send(argument.capture());
        assertThat(argument.getValue().actionLink().getPath()).isEqualTo("/finemp/activate");
        assertThat(argument.getValue().actionLink().getQuery()).isNull();
        assertThat(argument.getValue().actionLink().getFragment()).isEqualTo("token=" + token.rawValue());
        assertThat(argument.getValue().toString()).doesNotContain(token.rawValue()).doesNotContain("synthetic@");
    }

    @Test
    void shouldRejectUnsafeFrontendBaseUris() {
        for (String uri : new String[]{
                "http://app.example.invalid", "ftp://app.example.invalid", 
                "https://user:secret@app.example.invalid", "https://app.example.invalid?value=1",
                "https://app.example.invalid#fragment"}) {
            assertThatThrownBy(() -> new AccountActionMailDelivery(sender, URI.create(uri), true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldKeepDeliveryFailureFromChangingTheCommittedOutcome() {
        doThrow(new IllegalStateException("Synthetic provider failure"))
                .when(sender).send(any());
        AccountActionMailDelivery delivery = new AccountActionMailDelivery(
                sender, URI.create("https://app.example.invalid"), true);

        assertThatCode(() -> delivery.requested(new AccountActionRequested(
                "synthetic@example.invalid", AccountActionPurpose.PASSWORD_RESET,
                OpaqueAccountToken.generate(), Instant.parse("2026-01-02T00:00:00Z"))))
                .doesNotThrowAnyException();
    }
}
