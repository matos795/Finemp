package com.finemp.connector.transport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.connector.*;
import com.finemp.identity.OpaqueAccountToken;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ConnectorAuthenticationFilterTest {
    private final ConnectorCredentialVerifier verifier = mock(ConnectorCredentialVerifier.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    private final String raw = OpaqueAccountToken.generate().rawValue();
    private final ConnectorIdentity identity = new ConnectorIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
    private ConnectorAuthenticationFilter filter;
    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(now);
        filter = new ConnectorAuthenticationFilter(verifier, new ConnectorAuthenticationLimits(clock, 20, 10, 2));
        when(verifier.authenticate(raw)).thenReturn(Optional.of(identity));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void shouldAuthenticateOnlyBoundMachineIdentityAndNeverPersistTheRawSecretOrASession() throws Exception {
        var request = request(); var response = new MockHttpServletResponse(); var captured = new AtomicReference<Authentication>();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("browser", null, List.of()));
        filter.doFilter(request, response, (req, res) -> captured.set(SecurityContextHolder.getContext().getAuthentication()));
        assertThat(captured.get().getPrincipal()).isEqualTo(identity);
        assertThat(captured.get().getCredentials()).isNull();
        assertThat(captured.get().toString()).doesNotContain(raw, "browser");
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void shouldRejectPlainHttpSpoofedForwardingQueryTokensAndDuplicateOrOversizedHeadersBeforeDatabaseLookup() throws Exception {
        var insecure = request(); insecure.setSecure(false); insecure.addHeader("X-Forwarded-Proto", "https");
        var query = request(); query.setQueryString("access_token=" + raw);
        var duplicate = request(); duplicate.addHeader("Authorization", "Bearer " + raw);
        var large = request(); large.removeHeader("Authorization"); large.addHeader("Authorization", "Bearer " + "x".repeat(5000));
        for (var request : List.of(insecure, query, duplicate, large)) {
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(401); assertThat(chain.getRequest()).isNull();
            assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"CONNECTOR_UNAUTHORIZED\"}").doesNotContain(raw);
        }
        verifyNoInteractions(verifier);
    }
    @Test void shouldNeverUseABrowserContextWhenBearerIsMissingOrInvalid() throws Exception {
        var request = request(); request.removeHeader("Authorization");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("browser", null, List.of()));
        var response = new MockHttpServletResponse(); filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(verifier);
    }
    @Test void shouldKeepVerifierFailuresSanitizedAndAvoidContinuingWithStaleAuthentication() throws Exception {
        when(verifier.authenticate(raw)).thenThrow(new IllegalStateException("synthetic private SQL detail"));
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request(), response, chain);
        assertThat(response.getStatus()).isEqualTo(503); assertThat(response.getContentAsString()).doesNotContain("SQL", raw);
        assertThat(chain.getRequest()).isNull();
    }
    @Test void shouldLimitRepeatedAttemptsBeforeDatabaseWorkAndAllowTheNextWindow() throws Exception {
        when(verifier.authenticate(raw)).thenReturn(Optional.empty());
        for (int i = 0; i < 2; i++) filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
        var limited = new MockHttpServletResponse(); filter.doFilter(request(), limited, new MockFilterChain());
        assertThat(limited.getStatus()).isEqualTo(429); assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        verify(verifier, times(2)).authenticate(raw);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        var next = new MockHttpServletResponse(); filter.doFilter(request(), next, new MockFilterChain());
        assertThat(next.getStatus()).isEqualTo(401); verify(verifier, times(3)).authenticate(raw);
    }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/connector/v1/session");
        request.setSecure(true); request.addHeader("Authorization", "Bearer " + raw); return request;
    }
}
