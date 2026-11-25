package com.finemp.people;

import com.finemp.company.Company;
import com.finemp.finance.center.ResultCenter;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cadastro operacional mínimo: sem documento, nascimento, salário, endereço ou contato. */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 3)
    private EmploymentType employmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id")
    private JobPosition jobPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_center_id")
    private ResultCenter resultCenter;

    @Column(name = "hired_on", nullable = false)
    private LocalDate hiredOn;

    @Column(name = "terminated_on")
    private LocalDate terminatedOn;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() { }

    public Employee(Company company, EmployeeValues values) {
        if (company == null) throw new IllegalArgumentException("Funcionário inválido.");
        this.company = company;
        replace(values);
    }

    public void replace(EmployeeValues values) {
        if (values == null) throw new IllegalArgumentException("Funcionário inválido.");
        this.fullName = values.fullName();
        this.employmentType = values.employmentType();
        this.jobPosition = values.jobPosition();
        this.resultCenter = values.resultCenter();
        this.hiredOn = values.hiredOn();
        this.terminatedOn = values.terminatedOn();
    }

    public EmployeeStatus statusOn(LocalDate today) {
        return terminatedOn != null && !terminatedOn.isAfter(today)
                ? EmployeeStatus.TERMINATED
                : EmployeeStatus.ACTIVE;
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
    public String getFullName() { return fullName; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public JobPosition getJobPosition() { return jobPosition; }
    public ResultCenter getResultCenter() { return resultCenter; }
    public LocalDate getHiredOn() { return hiredOn; }
    public LocalDate getTerminatedOn() { return terminatedOn; }
    public long getVersion() { return version; }
}
