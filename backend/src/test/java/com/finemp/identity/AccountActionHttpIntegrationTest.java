package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.identity.delivery.AccountActionMail;
import com.finemp.identity.delivery.AccountActionMailSender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "finemp.identity.mail.enabled=true",
        "finemp.identity.mail.from=sender@example.invalid",
        "finemp.identity.mail.frontend-base-uri=https://app.example.invalid",
        "finemp.identity.rate-limit.reset-email=1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountActionHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountActionService service;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwords;
    @MockitoBean private AccountActionMailSender sender;

    private String email;

    @AfterEach
    void cleanupSyntheticAccount() {
        if (email != null) users.findByEmailIgnoreCase(email).ifPresent(user -> users.deleteById(user.getId()));
    }

    @Test
    void shouldReturnTheSameEmpty202ForUnknownInactiveAndRateLimitedAddresses() throws Exception {
        email = "neutral-" + UUID.randomUUID() + "@example.invalid";
        var inactive = new UserAccount("Synthetic Inactive", email, passwords.encode("synthetic123"));
        inactive.deactivate();
        users.saveAndFlush(inactive);
        for (String target : new String[]{"missing-" + UUID.randomUUID() + "@example.invalid", email, email}) {
            var response = mvc.perform(post("/api/auth/password-reset/request").with(csrf())
                            .contentType(APPLICATION_JSON).content("{\"email\":\"" + target + "\"}"))
                    .andExpect(status().isAccepted()).andReturn().getResponse();
            assertThat(response.getContentAsString()).isEmpty();
        }
        verifyNoInteractions(sender);
    }

    @Test
    void shouldResetThePasswordRejectReplayAndRevokeAnOlderSession() throws Exception {
        email = "reset-http-" + UUID.randomUUID() + "@example.invalid";
        users.saveAndFlush(new UserAccount("Synthetic Reset", email, passwords.encode("synthetic123")));
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"synthetic123\"}"))
                .andExpect(status().isNoContent()).andReturn();
        MockHttpSession oldSession = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        String token = deliveredToken();

        complete("/api/auth/password-reset/complete", token, 204);
        complete("/api/auth/password-reset/complete", token, 400);
        mvc.perform(get("/api/auth/me").session(oldSession)).andExpect(status().isUnauthorized());
        assertThat(oldSession.isInvalid()).isTrue();
        assertThat(passwords.matches("replacement123", users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void shouldActivateOnlyAnInvitationLinkAndRejectReplay() throws Exception {
        email = "activate-http-" + UUID.randomUUID() + "@example.invalid";
        service.inviteNewUser("Synthetic Invitation", email);
        String token = deliveredToken();

        complete("/api/auth/password-reset/complete", token, 400);
        complete("/api/auth/activate", token, 204);
        complete("/api/auth/activate", token, 400);

        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldRequireCsrfForPublicIdentityMutations() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"synthetic@example.invalid\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sender);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldNotEchoOrLogSecretsWhenPayloadsAreRejected(CapturedOutput output) throws Exception {
        String token = OpaqueAccountToken.generate().rawValue();
        String password = "synthetic-secret-".repeat(8);
        for (String body : new String[]{
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + password}) {
            var response = mvc.perform(post("/api/auth/password-reset/complete").with(csrf())
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertThat(response.getContentAsString()).isEmpty();
        }
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(password);
    }

    private String deliveredToken() {
        var argument = ArgumentCaptor.forClass(AccountActionMail.class);
        verify(sender).send(argument.capture());
        return argument.getValue().actionLink().getFragment().substring("token=".length());
    }

    private void complete(String route, String token, int expected) throws Exception {
        var response = mvc.perform(post(route).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"replacement123\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse();
        assertThat(response.getContentAsString()).isEmpty();
    }
}
