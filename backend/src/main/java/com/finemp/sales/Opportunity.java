package com.finemp.sales;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "sales_opportunities")
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_partner_id")
    private BusinessPartner businessPartner;

    @Embedded
    private OpportunityValues values;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpportunityStatus status;

    @Column(name = "won_at")
    private Instant wonAt;

    @Column(name = "lost_at")
    private Instant lostAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "opportunity", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("recordedAt ASC, id ASC")
    private List<OpportunityStageHistory> stageHistory = new ArrayList<>();

    protected Opportunity() { }

    public Opportunity(
            Company company,
            BusinessPartner businessPartner,
            OpportunityValues values,
            Instant now) {
        if (company == null || values == null || now == null) throw invalid();
        this.company = company;
        this.businessPartner = businessPartner;
        this.values = values;
        this.status = OpportunityStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
        this.stageHistory.add(new OpportunityStageHistory(this, values.stage(), now));
    }

    static Opportunity forTest(OpportunityValues values) {
        var opportunity = new Opportunity();
        opportunity.values = values;
        opportunity.status = OpportunityStatus.OPEN;
        opportunity.createdAt = Instant.EPOCH;
        opportunity.updatedAt = Instant.EPOCH;
        opportunity.stageHistory.add(new OpportunityStageHistory(
                opportunity, values.stage(), Instant.EPOCH));
        return opportunity;
    }

    public void replace(BusinessPartner partner, OpportunityValues next, Instant now) {
        if (next == null || now == null) throw invalid();
        if (!Objects.equals(values.stage(), next.stage())) {
            stageHistory.add(new OpportunityStageHistory(this, next.stage(), now));
        }
        businessPartner = partner;
        values = next;
        updatedAt = now;
    }

    public void moveStage(String stage, Instant now) {
        if (stage == null || stage.isBlank() || stage.length() > 80 || now == null) throw invalid();
        String normalized = stage.trim();
        if (Objects.equals(values.stage(), normalized)) return;
        values = new OpportunityValues(
                values.title(),
                normalized,
                values.monthlyAmount(),
                values.probability(),
                values.responsible(),
                values.expectedCloseOn(),
                values.notes());
        stageHistory.add(new OpportunityStageHistory(this, normalized, now));
        updatedAt = now;
    }

    public void win(Instant now) {
        if (now == null) throw invalid();
        if (status == OpportunityStatus.WON) return;
        status = OpportunityStatus.WON;
        wonAt = now;
        lostAt = null;
        updatedAt = now;
    }

    public void lose(Instant now) {
        if (now == null) throw invalid();
        if (status == OpportunityStatus.LOST) return;
        status = OpportunityStatus.LOST;
        lostAt = now;
        wonAt = null;
        updatedAt = now;
    }

    public void reopen(Instant now) {
        if (now == null) throw invalid();
        if (status == OpportunityStatus.OPEN) return;
        status = OpportunityStatus.OPEN;
        wonAt = null;
        lostAt = null;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public BusinessPartner getBusinessPartner() { return businessPartner; }
    public OpportunityValues getValues() { return values; }
    public OpportunityStatus getStatus() { return status; }
    public Instant getWonAt() { return wonAt; }
    public Instant getLostAt() { return lostAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OpportunityStageHistory> getStageHistory() { return List.copyOf(stageHistory); }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Oportunidade inválida.");
    }
}
