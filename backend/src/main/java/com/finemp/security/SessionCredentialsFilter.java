package com.finemp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Registered only inside Spring Security, never as a second servlet filter. */
public class SessionCredentialsFilter extends OncePerRequestFilter {

    private final SessionAccountStateValidator validator;

    public SessionCredentialsFilter(SessionAccountStateValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserAccountPrincipal principal
                && !validator.isCurrent(principal)) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        // Authorization still decides 401 on protected routes; public auth routes remain usable.
        chain.doFilter(request, response);
    }
}
