package com.finemp.connector;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "connector_credentials")
public class ConnectorCredential {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "accounting_firm_id", nullable = false, updatable = false) private UUID accountingFirmId;
    @Column(name = "accounting_source_id", nullable = false, updatable = false) private UUID accountingSourceId;
    @Column(nullable = false, updatable = false, length = 80) private String label;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "issued_by_user_id", nullable = false) private UUID issuedByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Version private long version;
    protected ConnectorCredential() { }
    public ConnectorCredential(UUID firm, UUID source, String label, String hash, UUID actor, Instant now, Instant expires) {
        accountingFirmId = Objects.requireNonNull(firm); accountingSourceId = Objects.requireNonNull(source);
        if (label == null || label.isBlank() || label.codePointCount(0, label.length()) > 80) throw new IllegalArgumentException("Invalid connector label.");
        this.label = label.trim(); createdAt = Objects.requireNonNull(now); replaceSecret(hash, actor, now, expires);
    }
    public void rotate(String hash, UUID actor, Instant now, Instant expires) {
        if (revokedAt != null) throw new IllegalStateException("Connector credential is revoked.");
        replaceSecret(hash, actor, now, expires);
    }
    private void replaceSecret(String hash, UUID actor, Instant now, Instant expires) {
        if (hash == null || !hash.matches("[a-f0-9]{64}") || now == null || now.isBefore(createdAt)
                || expires == null || !expires.isAfter(now)) throw new IllegalArgumentException("Invalid connector credential.");
        tokenHash = hash; issuedByUserId = Objects.requireNonNull(actor); expiresAt = expires;
    }
    public boolean revoke(Instant now) {
        if (revokedAt != null) return false;
        if (now == null || now.isBefore(createdAt)) throw new IllegalArgumentException("Invalid revocation time.");
        revokedAt = now; return true;
    }
    public boolean isUsableAt(Instant now) { return revokedAt == null && now != null && !now.isBefore(createdAt) && now.isBefore(expiresAt); }
    public UUID getId() { return id; }
    public UUID getAccountingFirmId() { return accountingFirmId; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public String getLabel() { return label; }
    public UUID getIssuedByUserId() { return issuedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public long getVersion() { return version; }
    @Override public String toString() { return "ConnectorCredential[id=" + id + ", secret=redacted]"; }
}
