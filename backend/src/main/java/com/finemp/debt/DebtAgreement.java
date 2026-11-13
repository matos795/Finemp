package com.finemp.debt;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "debt_agreements")
public class DebtAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_partner_id")
    private BusinessPartner businessPartner;

    @Embedded
    private DebtAgreementValues values;

    @OneToMany(
            mappedBy = "agreement",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<DebtInstallment> installments = new ArrayList<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DebtAgreement() {
    }

    public DebtAgreement(
            Company company,
            BusinessPartner businessPartner,
            DebtAgreementValues values) {
        if (company == null) throw invalid();
        this.company = company;
        replace(businessPartner, values);
    }

    public void replace(
            BusinessPartner businessPartner,
            DebtAgreementValues values) {
        if (values == null) throw invalid();
        requirePartnerShape(values.origin(), businessPartner);

        this.businessPartner = businessPartner;
        this.values = values;

        installments.clear();
        for (var row : values.schedule()) {
            installments.add(new DebtInstallment(this, row));
        }
    }

    private static void requirePartnerShape(
            DebtOrigin origin,
            BusinessPartner businessPartner) {
        if (origin == DebtOrigin.TAX) {
            if (businessPartner != null) throw invalid();
            return;
        }
        if (businessPartner == null) throw invalid();
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
    public DebtAgreementValues getValues() { return values; }
    public List<DebtInstallment> getInstallments() { return List.copyOf(installments); }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Acordo de dívida inválido.");
    }
}
