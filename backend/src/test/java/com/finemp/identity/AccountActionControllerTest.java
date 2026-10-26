package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountActionControllerTest {

    private final AccountActionService service = mock(AccountActionService.class);
    private final IdentityAttemptGuard guard = mock(IdentityAttemptGuard.class);
    private final AccountActionController controller = new AccountActionController(service, guard);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void shouldNotDiscloseThrottlingOrInvokeTheResetServiceWhenLimited() {
        var response = controller.requestReset(new PasswordResetRequest("synthetic@example.invalid"),
                new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(service);
    }

    @Test
    void shouldNotExposeTheTokenAfterRequestingAReset() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(guard.allowPasswordReset(request.getRemoteAddr(), "synthetic@example.invalid")).thenReturn(true);
        var response = controller.requestReset(new PasswordResetRequest("synthetic@example.invalid"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
        verify(service).requestPasswordReset("synthetic@example.invalid");
    }

    @Test
    void shouldInvalidateTheRequestSessionAfterACompletedAction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String token = OpaqueAccountToken.generate().rawValue();

        var response = controller.activate(new CompleteAccountActionRequest(token, "synthetic123"), request);

        verify(service).complete(token, AccountActionPurpose.INVITATION, "synthetic123");
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void shouldRedactTokensAndPasswordsFromDiagnosticRepresentations() {
        String token = OpaqueAccountToken.generate().rawValue();
        assertThat(new CompleteAccountActionRequest(token, "synthetic123").toString())
                .doesNotContain(token).doesNotContain("synthetic123");
        assertThat(new PasswordResetRequest("synthetic@example.invalid").toString()).doesNotContain("synthetic@");
    }
}
