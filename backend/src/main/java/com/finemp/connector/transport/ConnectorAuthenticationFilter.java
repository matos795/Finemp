package com.finemp.connector.transport;

import com.finemp.connector.ConnectorCredentialVerifier;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Created only in the stateless machine chain; never registered as a global servlet filter. */
public class ConnectorAuthenticationFilter extends OncePerRequestFilter {
    private final ConnectorCredentialVerifier verifier;
    private final ConnectorAuthenticationLimits limits;
    public ConnectorAuthenticationFilter(ConnectorCredentialVerifier verifier, ConnectorAuthenticationLimits limits) {
        this.verifier = verifier; this.limits = limits;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // A browser context or synthetic Authentication must never be a machine credential fallback.
        SecurityContextHolder.clearContext();
        var values = Collections.list(request.getHeaders("Authorization"));
        if (!request.isSecure() || (request.getQueryString() != null && !request.getQueryString().isBlank()) || values.size() != 1) {
            reject(response, 401, "CONNECTOR_UNAUTHORIZED"); return;
        }
        String value = values.getFirst();
        if (value.length() != 50 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response, 401, "CONNECTOR_UNAUTHORIZED"); return;
        }
        String raw = value.substring(7);
        if (!raw.matches("[A-Za-z0-9_-]{43}")) { reject(response, 401, "CONNECTOR_UNAUTHORIZED"); return; }
        if (!limits.allow(request.getRemoteAddr(), raw)) { reject(response, 429, "CONNECTOR_RATE_LIMITED"); return; }
        try {
            var identity = verifier.authenticate(raw);
            if (identity.isEmpty()) { reject(response, 401, "CONNECTOR_UNAUTHORIZED"); return; }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity.get(), null,
                    List.of(new SimpleGrantedAuthority("CONNECTOR_SOURCE"))));
            SecurityContextHolder.setContext(context);
        } catch (RuntimeException unavailable) { reject(response, 503, "CONNECTOR_UNAVAILABLE"); return; }
        try { chain.doFilter(request, response); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        if (status == 429) response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
