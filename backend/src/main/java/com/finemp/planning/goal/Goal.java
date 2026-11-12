package com.finemp.planning.goal;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "planning_goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalDirection direction;

    @Column(name = "baseline_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal baselineValue;

    @Column(name = "target_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetValue;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Goal() {
    }

    public Goal(
            Company company,
            String name,
            GoalDirection direction,
            BigDecimal baselineValue,
            BigDecimal targetValue,
            LocalDate dueOn,
            String notes) {

        if (company == null) throw new IllegalArgumentException("Empresa da meta inválida.");
        this.company = company;
        replace(name, direction, baselineValue, targetValue, dueOn, notes);
    }

    public void replace(
            String name,
            GoalDirection direction,
            BigDecimal baselineValue,
            BigDecimal targetValue,
            LocalDate dueOn,
            String notes) {

        if (name == null || name.isBlank() || name.trim().length() > 180) {
            throw new IllegalArgumentException("Nome da meta inválido.");
        }
        if (direction == null || baselineValue == null || targetValue == null || dueOn == null) {
            throw new IllegalArgumentException("Meta incompleta.");
        }

        var comparison = targetValue.compareTo(baselineValue);
        if ((direction == GoalDirection.UP && comparison <= 0)
                || (direction == GoalDirection.DOWN && comparison >= 0)) {
            throw new IllegalArgumentException("Alvo incompatível com o sentido da meta.");
        }

        this.name = name.trim();
        this.direction = direction;
        this.baselineValue = baselineValue;
        this.targetValue = targetValue;
        this.dueOn = dueOn;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void deactivate() {
        active = false;
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
    public String getName() { return name; }
    public GoalDirection getDirection() { return direction; }
    public BigDecimal getBaselineValue() { return baselineValue; }
    public BigDecimal getTargetValue() { return targetValue; }
    public LocalDate getDueOn() { return dueOn; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
