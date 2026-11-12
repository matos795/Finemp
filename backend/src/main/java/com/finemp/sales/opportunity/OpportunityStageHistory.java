package com.finemp.sales.opportunity;

import com.finemp.company.Company;
import com.finemp.sales.stage.SalesStage;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_opportunity_stage_history")
public class OpportunityStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false, updatable = false)
    private Opportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false, updatable = false)
    private SalesStage stage;

    @Column(name = "stage_name", nullable = false, length = 100, updatable = false)
    private String stageName;

    @Column(name = "stage_position", nullable = false, updatable = false)
    private int stagePosition;

    @Column(name = "actor_user_account_id", nullable = false, updatable = false)
    private UUID actorUserAccountId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected OpportunityStageHistory() {
    }

    public OpportunityStageHistory(
            Opportunity opportunity,
            SalesStage stage,
            UUID actorUserAccountId,
            Instant occurredAt) {

        if (opportunity == null || stage == null
                || actorUserAccountId == null || occurredAt == null) {
            throw new IllegalArgumentException("Histórico de etapa inválido.");
        }

        this.company = opportunity.getCompany();
        this.opportunity = opportunity;
        this.stage = stage;
        this.stageName = stage.getName();
        this.stagePosition = stage.getPosition();
        this.actorUserAccountId = actorUserAccountId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Opportunity getOpportunity() { return opportunity; }
    public SalesStage getStage() { return stage; }
    public String getStageName() { return stageName; }
    public int getStagePosition() { return stagePosition; }
    public UUID getActorUserAccountId() { return actorUserAccountId; }
    public Instant getOccurredAt() { return occurredAt; }
}
