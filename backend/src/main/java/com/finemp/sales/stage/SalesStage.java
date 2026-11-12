package com.finemp.sales.stage;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_stages")
public class SalesStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SalesStage() {
    }

    public SalesStage(Company company, String name, int position) {
        if (company == null) {
            throw new IllegalArgumentException("Empresa da etapa inválida.");
        }
        this.company = company;
        replace(name, position);
    }

    public void replace(String name, int position) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Nome da etapa inválido.");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Posição da etapa inválida.");
        }

        this.name = name.trim();
        this.position = position;
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
    public int getPosition() { return position; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
