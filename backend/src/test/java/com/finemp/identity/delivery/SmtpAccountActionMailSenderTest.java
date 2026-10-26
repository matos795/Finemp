package com.finemp.identity.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.finemp.identity.AccountActionPurpose;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@ExtendWith(MockitoExtension.class)
class SmtpAccountActionMailSenderTest {

    @Mock
    private JavaMailSender sender;

    @Test
    void shouldSendOnlyASingleUseLinkInsteadOfAPassword() {
        var adapter = new SmtpAccountActionMailSender(sender, "sender@example.invalid");
        URI link = URI.create("https://app.example.invalid/activate#token=synthetic-token");

        adapter.send(new AccountActionMail("recipient@example.invalid", link,
                AccountActionPurpose.INVITATION, Instant.parse("2026-01-02T00:00:00Z")));

        var argument = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(argument.capture());
        assertThat(argument.getValue().getTo()).containsExactly("recipient@example.invalid");
        assertThat(argument.getValue().getText()).contains(link.toString()).contains("uso único");
    }

    @Test
    void shouldSetFiniteDefaultTimeoutsAndDisableProtocolDebug() {
        JavaMailSenderImpl implementation = new JavaMailSenderImpl();
        implementation.getJavaMailProperties().setProperty("mail.debug", "true");
        new SmtpAccountActionMailSender(implementation, "sender@example.invalid");

        assertThat(implementation.getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000")
                .containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.debug", "false");
    }

    @Test
    void shouldRejectHeaderInjection() {
        var adapter = new SmtpAccountActionMailSender(sender, "sender@example.invalid");

        assertThatThrownBy(() -> adapter.send(new AccountActionMail(
                "recipient@example.invalid\r\nBcc:other@example.invalid",
                URI.create("https://app.example.invalid/reset-password#token=synthetic-token"),
                AccountActionPurpose.PASSWORD_RESET, Instant.parse("2026-01-02T00:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
