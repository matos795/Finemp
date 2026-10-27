package com.finemp.audit;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "access_audit_events")
public class AccessAuditEvent {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 80, updatable = false)
    private AccessAuditAction action;

    @Column(name = "actor_user_account_id", updatable = false)
    private UUID actorUserAccountId;

    @Column(name = "subject_user_account_id", nullable = false, updatable = false)
    private UUID subjectUserAccountId;

    @Column(name = "accounting_firm_id", updatable = false)
    private UUID accountingFirmId;

    @Column(name = "company_id", updatable = false)
    private UUID companyId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "access_audit_event_permissions", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "permission", nullable = false, length = 80)
    private Set<String> permissionSnapshot = new HashSet<>();

    protected AccessAuditEvent() { }

    AccessAuditEvent(AccessAuditAction action, UUID actorUserAccountId, UUID subjectUserAccountId,
                     UUID accountingFirmId, UUID companyId, Instant occurredAt,
                     Set<? extends Enum<?>> permissions) {
        this.action = Objects.requireNonNull(action);
        this.actorUserAccountId = actorUserAccountId;
        this.subjectUserAccountId = Objects.requireNonNull(subjectUserAccountId);
        this.accountingFirmId = accountingFirmId;
        this.companyId = companyId;
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.permissionSnapshot = Set.copyOf(permissions).stream().map(Enum::name)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public UUID getId() { return id; }
}
