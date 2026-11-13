package com.finemp.sales;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
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
    private String subject;

    @Column(nullable = false, length = 160)
    private String responsible;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SalesActivity() { }

    public SalesActivity(
            Company company,
            Opportunity opportunity,
            BusinessPartner businessPartner,
            String subject,
            String responsible,
            LocalDate dueOn,
            String notes,
            Instant now) {
        if (company == null || now == null) throw invalid();
        this.company = company;
        this.createdAt = now;
        replace(opportunity, businessPartner, subject, responsible, dueOn, notes, now);
    }

    public void replace(
            Opportunity opportunity,
            BusinessPartner businessPartner,
            String subject,
            String responsible,
            LocalDate dueOn,
            String notes,
            Instant now) {
        this.subject = text(subject, 180, 2);
        this.responsible = text(responsible, 160, 1);
        if (dueOn == null || now == null || notes != null && notes.length() > 4000) throw invalid();
        this.opportunity = opportunity;
        this.businessPartner = businessPartner;
        this.dueOn = dueOn;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        if (now == null) throw invalid();
        completedAt = now;
        updatedAt = now;
    }

    public void reopen(Instant now) {
        if (now == null) throw invalid();
        completedAt = null;
        updatedAt = now;
    }

    public SalesActivityDueStatus dueStatus(LocalDate today) {
        return SalesActivityDueStatus.of(dueOn, completedAt != null, today);
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Opportunity getOpportunity() { return opportunity; }
    public BusinessPartner getBusinessPartner() { return businessPartner; }
    public String getSubject() { return subject; }
    public String getResponsible() { return responsible; }
    public LocalDate getDueOn() { return dueOn; }
    public String getNotes() { return notes; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String text(String value, int max, int min) {
        if (value == null || value.length() > max) throw invalid();
        String normalized = value.trim();
        if (normalized.length() < min) throw invalid();
        return normalized;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Atividade comercial inválida.");
    }
}
