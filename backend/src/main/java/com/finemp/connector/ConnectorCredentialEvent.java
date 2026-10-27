package com.finemp.connector;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "connector_credential_events")
public class ConnectorCredentialEvent {
    public enum Action { ISSUED, ROTATED, REVOKED }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false, updatable = false) private ConnectorCredential credential;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 20) private Action action;
    @Column(name = "actor_user_id", nullable = false, updatable = false) private UUID actorUserId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    protected ConnectorCredentialEvent() { }
    public ConnectorCredentialEvent(ConnectorCredential credential, Action action, UUID actor, Instant now) {
        this.credential = Objects.requireNonNull(credential); this.action = Objects.requireNonNull(action);
        actorUserId = Objects.requireNonNull(actor); occurredAt = Objects.requireNonNull(now);
    }
}
