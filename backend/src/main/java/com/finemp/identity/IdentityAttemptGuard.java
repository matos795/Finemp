package com.finemp.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdentityAttemptGuard {

    private final FixedWindowAttemptLimiter limiter;
    private final int loginIpLimit;
    private final int loginPairLimit;
    private final int resetIpLimit;
    private final int resetEmailLimit;
    private final int completionIpLimit;
    private final int invitationActorLimit;
    private final Duration window;

    public IdentityAttemptGuard(
            Clock clock,
            @Value("${finemp.identity.rate-limit.capacity:10000}") int capacity,
            @Value("${finemp.identity.rate-limit.login-ip:60}") int loginIpLimit,
            @Value("${finemp.identity.rate-limit.login-pair:10}") int loginPairLimit,
            @Value("${finemp.identity.rate-limit.reset-ip:20}") int resetIpLimit,
            @Value("${finemp.identity.rate-limit.reset-email:3}") int resetEmailLimit,
            @Value("${finemp.identity.rate-limit.completion-ip:30}") int completionIpLimit,
            @Value("${finemp.identity.rate-limit.invitation-actor:30}") int invitationActorLimit,
            @Value("${finemp.identity.rate-limit.window:PT15M}") Duration window) {
        if (loginIpLimit < 1 || loginPairLimit < 1 || resetIpLimit < 1 || resetEmailLimit < 1
                || completionIpLimit < 1 || invitationActorLimit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Identity rate limits must be positive.");
        }
        this.limiter = new FixedWindowAttemptLimiter(clock, capacity);
        this.loginIpLimit = loginIpLimit;
        this.loginPairLimit = loginPairLimit;
        this.resetIpLimit = resetIpLimit;
        this.resetEmailLimit = resetEmailLimit;
        this.completionIpLimit = completionIpLimit;
        this.invitationActorLimit = invitationActorLimit;
        this.window = window;
    }

    public void requireLogin(String remoteAddress, String email) {
        if (!limiter.tryAcquire(key("login-ip", remoteAddress), loginIpLimit, window)
                || !limiter.tryAcquire(key("login-pair", remoteAddress + "|" + normalize(email)), loginPairLimit, window)) {
            throw new TooManyIdentityAttemptsException();
        }
    }

    /** False must have the same public response as a delivered or unknown account. */
    public boolean allowPasswordReset(String remoteAddress, String email) {
        return limiter.tryAcquire(key("reset-ip", remoteAddress), resetIpLimit, window)
                && limiter.tryAcquire(key("reset-email", normalize(email)), resetEmailLimit, window);
    }

    public void requireCompletion(String remoteAddress) {
        if (!limiter.tryAcquire(key("complete-ip", remoteAddress), completionIpLimit, window)) {
            throw new TooManyIdentityAttemptsException();
        }
    }

    public void requireInvitation(java.util.UUID actorId) {
        if (!limiter.tryAcquire(key("invite-actor", actorId.toString()), invitationActorLimit, window)) {
            throw new TooManyIdentityAttemptsException();
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String key(String purpose, String value) {
        return purpose + ":" + OpaqueAccountToken.hash(value == null ? "unknown" : value);
    }
}
