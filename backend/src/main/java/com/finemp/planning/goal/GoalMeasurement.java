package com.finemp.planning.goal;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goal_measurements")
public class GoalMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false, updatable = false)
    private Goal goal;

    @Column(name = "measured_on", nullable = false, updatable = false)
    private LocalDate measuredOn;

    @Column(name = "measured_value", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal measuredValue;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GoalMeasurement() {
    }

    public GoalMeasurement(
            Goal goal,
            LocalDate measuredOn,
            BigDecimal measuredValue,
            String notes) {

        if (goal == null || measuredOn == null || measuredValue == null) {
            throw new IllegalArgumentException("Medição da meta inválida.");
        }

        this.goal = goal;
        this.company = goal.getCompany();
        this.measuredOn = measuredOn;
        this.measuredValue = measuredValue;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Goal getGoal() { return goal; }
    public LocalDate getMeasuredOn() { return measuredOn; }
    public BigDecimal getMeasuredValue() { return measuredValue; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
