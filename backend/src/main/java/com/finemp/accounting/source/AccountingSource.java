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
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_mode", nullable = false, length = 20)
    private AccountingConnectionMode connectionMode = AccountingConnectionMode.DIRECT;

    protected AccountingSource() {
    }

    public AccountingSource(
            Company company,
            AccountingSourceType type,
            String externalReference,
            boolean active) {
        this.company = company;
        this.type = type;
        if (type == AccountingSourceType.FILE_IMPORT) {
            if (externalReference != null && !externalReference.isBlank()) {
                throw new IllegalArgumentException("File import source must not have an external reference.");
            }
            this.externalReference = null;
            this.connectionMode = AccountingConnectionMode.UPLOAD;
        } else {
            this.externalReference = externalReference;
            this.connectionMode = AccountingConnectionMode.DIRECT;
        }
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

    public void updateExternalReference(String externalReference) {
        if (type == AccountingSourceType.FILE_IMPORT) {
            throw new IllegalArgumentException("File import source has no external reference.");
        }
        if (externalReference == null || externalReference.isBlank() || externalReference.length() > 255) {
            throw new IllegalArgumentException("Invalid accounting source external reference.");
        }
        this.externalReference = externalReference.trim();
    }

    public boolean isActive() {
        return active;
    }
    public AccountingConnectionMode getConnectionMode() { return connectionMode; }
    public void reserveConnectorRoute() {
        if (type != AccountingSourceType.QUESTOR) {
            throw new IllegalStateException("Only Questor sources can use the Connector route.");
        }
        connectionMode = AccountingConnectionMode.CONNECTOR;
    }
}
