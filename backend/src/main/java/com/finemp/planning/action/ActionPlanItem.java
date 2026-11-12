package com.finemp.planning.action;

import com.finemp.company.Company;
import com.finemp.planning.goal.Goal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "action_plan_items")
public class ActionPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(name = "what_text", nullable = false, length = 240)
    private String what;

    @Column(name = "why_text", columnDefinition = "TEXT")
    private String why;

    @Column(nullable = false, length = 160)
    private String responsible;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "where_text", length = 200)
    private String where;

    @Column(name = "how_text", columnDefinition = "TEXT")
    private String how;

    @Column(precision = 19, scale = 2)
    private BigDecimal cost;

    @Column(name = "workflow_status", nullable = false, length = 40)
    private String workflowStatus;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActionPlanItem() {
    }

    public ActionPlanItem(
            Company company,
            Goal goal,
            String what,
            String why,
            String responsible,
            LocalDate dueOn,
            String where,
            String how,
            BigDecimal cost,
            String workflowStatus) {

        if (company == null) throw new IllegalArgumentException("Empresa da ação inválida.");
        this.company = company;
        replace(goal, what, why, responsible, dueOn, where, how, cost, workflowStatus);
    }

    public void replace(
            Goal goal,
            String what,
            String why,
            String responsible,
            LocalDate dueOn,
            String where,
            String how,
            BigDecimal cost,
            String workflowStatus) {

        if (what == null || what.isBlank() || what.trim().length() > 240) {
            throw new IllegalArgumentException("Ação inválida.");
        }
        if (responsible == null || responsible.isBlank() || responsible.trim().length() > 160) {
            throw new IllegalArgumentException("Responsável inválido.");
        }
        if (cost != null && cost.signum() < 0) {
            throw new IllegalArgumentException("Custo da ação inválido.");
        }
        if (workflowStatus == null || workflowStatus.isBlank() || workflowStatus.trim().length() > 40) {
            throw new IllegalArgumentException("Status da ação inválido.");
        }

        this.goal = goal;
        this.what = what.trim();
        this.why = optional(why);
        this.responsible = responsible.trim();
        this.dueOn = dueOn;
        this.where = optional(where);
        this.how = optional(how);
        this.cost = cost;
        this.workflowStatus = workflowStatus.trim();
    }

    public void complete(Instant when) {
        completedAt = java.util.Objects.requireNonNull(when);
    }

    public void reopen() {
        completedAt = null;
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
    public Goal getGoal() { return goal; }
    public String getWhat() { return what; }
    public String getWhy() { return why; }
    public String getResponsible() { return responsible; }
    public LocalDate getDueOn() { return dueOn; }
    public String getWhere() { return where; }
    public String getHow() { return how; }
    public BigDecimal getCost() { return cost; }
    public String getWorkflowStatus() { return workflowStatus; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
