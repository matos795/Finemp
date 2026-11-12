package com.finemp.sales.activity;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.sales.opportunity.Opportunity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sales_activities")
public class SalesActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunity_id")
    private Opportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_partner_id")
    private BusinessPartner businessPartner;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 160)
    private String responsible;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SalesActivity() {
    }

    public SalesActivity(
            Company company,
            Opportunity opportunity,
            BusinessPartner businessPartner,
            String title,
            String notes,
            String responsible,
            LocalDate dueOn) {

        if (company == null) {
            throw new IllegalArgumentException("Empresa da atividade inválida.");
        }
        this.company = company;
        replace(
                opportunity,
                businessPartner,
                title,
                notes,
                responsible,
                dueOn);
    }

    public void replace(
            Opportunity opportunity,
            BusinessPartner businessPartner,
            String title,
            String notes,
            String responsible,
            LocalDate dueOn) {

        if (title == null || title.isBlank() || title.trim().length() > 180) {
            throw new IllegalArgumentException("Título da atividade inválido.");
        }
        if (responsible == null || responsible.isBlank() || responsible.trim().length() > 160) {
            throw new IllegalArgumentException("Responsável inválido.");
        }
        if (dueOn == null) {
            throw new IllegalArgumentException("Prazo da atividade inválido.");
        }

        this.opportunity = opportunity;
        this.businessPartner = businessPartner;
        this.title = title.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.responsible = responsible.trim();
        this.dueOn = dueOn;
    }

    public void complete(Instant when) {
        completedAt = java.util.Objects.requireNonNull(when);
        cancelledAt = null;
    }

    public void cancel(Instant when) {
        cancelledAt = java.util.Objects.requireNonNull(when);
        completedAt = null;
    }

    public void reopen() {
        completedAt = null;
        cancelledAt = null;
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
    public Opportunity getOpportunity() { return opportunity; }
    public BusinessPartner getBusinessPartner() { return businessPartner; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public String getResponsible() { return responsible; }
    public LocalDate getDueOn() { return dueOn; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public long getVersion() { return version; }
}
