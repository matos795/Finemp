package com.finemp.identity;

import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal identity use cases. Administrative HTTP adapters must authorize invitations. */
@Service
public class AccountActionService {

    private final UserAccountRepository userAccountRepository;
    private final AccountActionTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration invitationTtl;
    private final Duration resetTtl;

    public AccountActionService(
            UserAccountRepository userAccountRepository,
            AccountActionTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${finemp.identity.invitation-ttl:PT24H}") Duration invitationTtl,
            @Value("${finemp.identity.password-reset-ttl:PT30M}") Duration resetTtl) {
        if (invitationTtl.isZero() || invitationTtl.isNegative()
                || resetTtl.isZero() || resetTtl.isNegative()) {
            throw new IllegalArgumentException("Account action expiration must be positive.");
        }
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.invitationTtl = invitationTtl;
        this.resetTtl = resetTtl;
    }

    @Transactional
    public void inviteNewUser(String name, String email) {
        String normalizedEmail = normalizeEmail(email);
        userAccountRepository.insertInvitedIfAbsent(
                java.util.UUID.randomUUID(), name.trim(), normalizedEmail, clock.instant());
        java.util.UUID userAccountId = userAccountRepository.findIdByEmailIgnoreCase(normalizedEmail)
                .orElseThrow();

        UserAccount locked = userAccountRepository.findLockedById(userAccountId).orElseThrow();
        if (!locked.isInvitationPending()) {
            return;
        }
        issue(locked, AccountActionPurpose.INVITATION, invitationTtl);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userAccountRepository.findIdByEmailIgnoreCase(normalizeEmail(email)).ifPresent(userAccountId -> {
            UserAccount locked = userAccountRepository.findLockedById(userAccountId).orElseThrow();
            if (locked.isActive()) {
                issue(locked, AccountActionPurpose.PASSWORD_RESET, resetTtl);
            }
        });
    }

    @Transactional
    public void complete(
            String rawToken,
            AccountActionPurpose purpose,
            String newPassword) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidAccountActionException();
        }
        if (!AccountPasswordPolicy.isValid(newPassword)) {
            throw new InvalidAccountPasswordException();
        }

        AccountActionTokenReference reference = tokenRepository
                .findReferenceByTokenHash(OpaqueAccountToken.hash(rawToken))
                .orElseThrow(InvalidAccountActionException::new);

        // All issuance/consumption locks the user first. The projection above
        // avoids caching a stale token entity while another transaction consumes it.
        UserAccount userAccount = userAccountRepository.findLockedById(reference.userAccountId())
                .orElseThrow(InvalidAccountActionException::new);
        AccountActionToken token = tokenRepository.findById(reference.id())
                .orElseThrow(InvalidAccountActionException::new);
        Instant now = clock.instant();
        boolean allowedState = purpose == AccountActionPurpose.INVITATION
                ? userAccount.isInvitationPending()
                : userAccount.isActive();
        if (!allowedState || !token.isUsable(purpose, now)
                || !token.matchesCredentialsVersion(userAccount.getCredentialsVersion())) {
            throw new InvalidAccountActionException();
        }

        String passwordHash = passwordEncoder.encode(newPassword);
        if (purpose == AccountActionPurpose.INVITATION) {
            userAccount.completeInvitation(passwordHash);
        } else {
            userAccount.changePassword(passwordHash);
        }
        token.consume(now);
        invalidateUnconsumedTokens(userAccount, now);
    }

    private void issue(UserAccount userAccount, AccountActionPurpose purpose, Duration ttl) {
        Instant now = clock.instant();
        invalidateUnconsumedTokens(userAccount, now);
        OpaqueAccountToken token = OpaqueAccountToken.generate();
        Instant expiresAt = now.plus(ttl);
        tokenRepository.save(new AccountActionToken(userAccount, purpose, token.hash(), now, expiresAt));
        eventPublisher.publishEvent(new AccountActionRequested(userAccount.getEmail(), purpose, token, expiresAt));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void invalidateUnconsumedTokens(UserAccount userAccount, Instant now) {
        tokenRepository.findAllByUserAccountIdAndConsumedAtIsNull(userAccount.getId())
                .forEach(token -> token.consume(now));
    }
}
