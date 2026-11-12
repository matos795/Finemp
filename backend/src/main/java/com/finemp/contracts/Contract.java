package com.finemp.contracts;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_partner_id", nullable = false)
    private BusinessPartner businessPartner;

    @Embedded
    private ContractValues values;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contract() { }

    public Contract(Company company, BusinessPartner businessPartner, ContractValues values) {
        if (company == null || businessPartner == null || values == null) throw invalid();
        this.company = company;
        replace(businessPartner, values);
    }

    public void replace(BusinessPartner businessPartner, ContractValues values) {
        if (businessPartner == null || values == null) throw invalid();
        this.businessPartner = businessPartner;
        this.values = values;
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
    public BusinessPartner getBusinessPartner() { return businessPartner; }
    public ContractValues getValues() { return values; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Contrato inválido.");
    }
}
