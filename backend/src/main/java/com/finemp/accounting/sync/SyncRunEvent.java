package com.finemp.accounting.sync;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sync_run_events")
public class SyncRunEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_run_id", nullable = false, updatable = false) private SyncRun syncRun;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 30) private SyncRunStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "actor_type", nullable = false, updatable = false, length = 20) private SyncRunActorType actorType;
    @Column(name = "actor_id", updatable = false) private UUID actorId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "transition_index", nullable = false, updatable = false) private int transitionIndex;
    protected SyncRunEvent() { }
    public SyncRunEvent(SyncRun run, SyncRunActorType actorType, UUID actorId, Instant now) {
        syncRun = Objects.requireNonNull(run); status = run.getStatus(); this.actorType = Objects.requireNonNull(actorType);
        transitionIndex = switch (status) { case REQUESTED -> 0; case RUNNING -> 1; default -> 2; };
        if ((actorType == SyncRunActorType.SYSTEM) != (actorId == null)) throw new IllegalArgumentException("Invalid sync actor.");
        if (now == null || now.isBefore(run.getRequestedAt())) throw new IllegalArgumentException("Invalid sync event time.");
        this.actorId = actorId; occurredAt = now;
    }
    public UUID getId() { return id; }
    public SyncRunStatus getStatus() { return status; }
    public SyncRunActorType getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getTransitionIndex() { return transitionIndex; }
}

