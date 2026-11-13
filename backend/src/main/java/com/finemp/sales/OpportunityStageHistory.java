package com.finemp.sales;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "opportunity_stage_history")
public class OpportunityStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false, updatable = false)
    private Opportunity opportunity;

    @Column(nullable = false, length = 80, updatable = false)
    private String stage;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected OpportunityStageHistory() { }

    OpportunityStageHistory(Opportunity opportunity, String stage, Instant recordedAt) {
        if (opportunity == null || stage == null || stage.isBlank() || recordedAt == null) {
            throw new IllegalArgumentException("Histórico de etapa inválido.");
        }
        this.opportunity = opportunity;
        this.stage = stage.trim();
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public String getStage() { return stage; }
    public Instant getRecordedAt() { return recordedAt; }
}
