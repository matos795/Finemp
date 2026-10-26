package com.finemp.identity.delivery;

import com.finemp.identity.AccountActionPurpose;
import com.finemp.identity.AccountActionRequested;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public class AccountActionMailDelivery {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountActionMailDelivery.class);

    private final AccountActionMailSender sender;
    private final URI frontendBaseUri;

    public AccountActionMailDelivery(
            AccountActionMailSender sender,
            URI frontendBaseUri,
            boolean requireHttps) {
        String scheme = frontendBaseUri.getScheme();
        if (frontendBaseUri.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || !requireHttps && "http".equalsIgnoreCase(scheme))
                || frontendBaseUri.getUserInfo() != null
                || frontendBaseUri.getQuery() != null
                || frontendBaseUri.getFragment() != null) {
            throw new IllegalArgumentException("A valid frontend base URI without credentials, query or fragment is required.");
        }
        this.sender = sender;
        this.frontendBaseUri = frontendBaseUri;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requested(AccountActionRequested event) {
        String path = event.purpose() == AccountActionPurpose.INVITATION ? "activate" : "reset-password";
        String base = frontendBaseUri.toString();
        URI link = URI.create(base + (base.endsWith("/") ? "" : "/") + path
                + "#token=" + event.token().rawValue());
        try {
            sender.send(new AccountActionMail(event.email(), link, event.purpose(), event.expiresAt()));
        } catch (RuntimeException exception) {
            // Providers may embed message contents in exceptions. Do not log them.
            LOGGER.warn("Account action delivery failed for purpose {}.", event.purpose());
        }
    }
}
