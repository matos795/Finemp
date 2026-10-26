package com.finemp.identity;

import com.finemp.access.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_action_tokens")
public class AccountActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private AccountActionPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "credentials_version", nullable = false, updatable = false)
    private long credentialsVersion;

    protected AccountActionToken() {
    }

    public AccountActionToken(
            UserAccount userAccount,
            AccountActionPurpose purpose,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt) {
        this.userAccount = userAccount;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.credentialsVersion = userAccount.getCredentialsVersion();
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean matchesCredentialsVersion(long version) {
        return credentialsVersion == version;
    }

    public boolean isUsable(AccountActionPurpose expectedPurpose, Instant now) {
        return purpose == expectedPurpose && consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
