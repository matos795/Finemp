package com.finemp.sales.opportunity;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.sales.stage.SalesStage;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private SalesStage stage;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "monthly_recurring", nullable = false)
    private boolean monthlyRecurring;

    @Column(nullable = false)
    private int probability;

    @Column(nullable = false, length = 160)
    private String responsible;

    @Column(name = "expected_close_on", nullable = false)
    private LocalDate expectedCloseOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OpportunityOutcome outcome = OpportunityOutcome.OPEN;

    @Column(name = "opened_on", nullable = false, updatable = false)
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Opportunity() {
    }

    public Opportunity(
            Company company,
            BusinessPartner businessPartner,
            SalesStage stage,
            String title,
            BigDecimal amount,
            boolean monthlyRecurring,
            int probability,
            String responsible,
            LocalDate expectedCloseOn,
            LocalDate openedOn,
            String notes) {

        if (company == null || stage == null || openedOn == null) {
            throw new IllegalArgumentException("Oportunidade incompleta.");
        }

        this.company = company;
        this.stage = stage;
        this.openedOn = openedOn;
        replace(
                businessPartner,
                title,
                amount,
                monthlyRecurring,
                probability,
                responsible,
                expectedCloseOn,
                notes);
    }

    public void replace(
            BusinessPartner businessPartner,
            String title,
            BigDecimal amount,
            boolean monthlyRecurring,
            int probability,
            String responsible,
            LocalDate expectedCloseOn,
            String notes) {

        if (title == null || title.isBlank() || title.trim().length() > 180) {
            throw new IllegalArgumentException("Título da oportunidade inválido.");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Valor da oportunidade inválido.");
        }
        if (probability < 0 || probability > 100) {
            throw new IllegalArgumentException("Probabilidade inválida.");
        }
        if (responsible == null || responsible.isBlank() || responsible.trim().length() > 160) {
            throw new IllegalArgumentException("Responsável inválido.");
        }
        if (expectedCloseOn == null) {
            throw new IllegalArgumentException("Previsão de fechamento inválida.");
        }

        this.businessPartner = businessPartner;
        this.title = title.trim();
        this.amount = amount;
        this.monthlyRecurring = monthlyRecurring;
        this.probability = probability;
        this.responsible = responsible.trim();
        this.expectedCloseOn = expectedCloseOn;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void moveTo(SalesStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("Etapa inválida.");
        }
        this.stage = stage;
    }

    public void win(LocalDate closedOn) {
        if (closedOn == null || closedOn.isBefore(openedOn)) {
            throw new IllegalArgumentException("Data de ganho inválida.");
        }
        if (outcome == OpportunityOutcome.LOST) {
            throw new IllegalStateException("Reabra a oportunidade antes de marcá-la como ganha.");
        }
        outcome = OpportunityOutcome.WON;
        this.closedOn = closedOn;
    }

    public void lose(LocalDate closedOn) {
        if (closedOn == null || closedOn.isBefore(openedOn)) {
            throw new IllegalArgumentException("Data de perda inválida.");
        }
        if (outcome == OpportunityOutcome.WON) {
            throw new IllegalStateException("Reabra a oportunidade antes de marcá-la como perdida.");
        }
        outcome = OpportunityOutcome.LOST;
        this.closedOn = closedOn;
    }

    public void reopen() {
        outcome = OpportunityOutcome.OPEN;
        closedOn = null;
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public BusinessPartner getBusinessPartner() { return businessPartner; }
    public SalesStage getStage() { return stage; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public boolean isMonthlyRecurring() { return monthlyRecurring; }
    public int getProbability() { return probability; }
    public String getResponsible() { return responsible; }
    public LocalDate getExpectedCloseOn() { return expectedCloseOn; }
    public OpportunityOutcome getOutcome() { return outcome; }
    public LocalDate getOpenedOn() { return openedOn; }
    public LocalDate getClosedOn() { return closedOn; }
    public String getNotes() { return notes; }
    public long getVersion() { return version; }
}
