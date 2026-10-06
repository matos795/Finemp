package com.finemp.accounting.source;

import com.finemp.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "accounting_sources")
public class AccountingSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AccountingSourceType type;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AccountingSource() {
    }

    public AccountingSource(
            Company company,
            AccountingSourceType type,
            String externalReference,
            boolean active) {
        this.company = company;
        this.type = type;
        this.externalReference = externalReference;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public AccountingSourceType getType() {
        return type;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public boolean isActive() {
        return active;
    }
}