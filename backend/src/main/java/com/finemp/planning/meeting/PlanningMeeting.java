package com.finemp.planning.meeting;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "planning_meetings")
public class PlanningMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 80)
    private String stage;

    @Column(name = "scheduled_on", nullable = false)
    private LocalDate scheduledOn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "diagnostic_text", columnDefinition = "TEXT")
    private String diagnosticText;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanningMeeting() {
    }

    public PlanningMeeting(
            Company company,
            String title,
            String stage,
            LocalDate scheduledOn,
            String notes,
            String diagnosticText) {

        this.company = requireCompany(company);
        replace(title, stage, scheduledOn, notes, diagnosticText);
    }

    public void replace(
            String title,
            String stage,
            LocalDate scheduledOn,
            String notes,
            String diagnosticText) {

        this.title = required(title, 160, "Título do encontro inválido.");
        this.stage = required(stage, 80, "Etapa do encontro inválida.");
        this.scheduledOn = java.util.Objects.requireNonNull(
                scheduledOn,
                "Data do encontro inválida.");
        this.notes = optional(notes);
        this.diagnosticText = optional(diagnosticText);
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
    public String getTitle() { return title; }
    public String getStage() { return stage; }
    public LocalDate getScheduledOn() { return scheduledOn; }
    public String getNotes() { return notes; }
    public String getDiagnosticText() { return diagnosticText; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static Company requireCompany(Company company) {
        if (company == null) throw new IllegalArgumentException("Empresa do encontro inválida.");
        return company;
    }

    private static String required(String value, int max, String message) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
