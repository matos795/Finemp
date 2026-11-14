package com.finemp.assets;

import com.finemp.company.Company;
import com.finemp.finance.center.ResultCenter;
import com.finemp.finance.partner.BusinessPartner;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "asset_tag", length = 30)
    private String assetTag;

    @Column(nullable = false, length = 150)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssetCategory category;

    @Column(name = "brand_model", length = 80)
    private String brandModel;

    @Column(name = "serial_number", length = 60)
    private String serialNumber;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal acquisitionValue;

    @Column(nullable = false)
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_partner_id")
    private BusinessPartner businessPartner;

    @Column(name = "document_reference", length = 60)
    private String documentReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_center_id")
    private ResultCenter resultCenter;

    @Column(length = 80)
    private String location;

    @Column(length = 120)
    private String responsible;

    @Column(name = "useful_life_months", nullable = false)
    private int usefulLifeMonths;

    @Column(name = "residual_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal residualPercent;

    @Column(nullable = false)
    private boolean depreciable;

    @Column(name = "depreciation_start_date")
    private LocalDate depreciationStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    @Column(length = 800)
    private String notes;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Asset() { }

    public Asset(Company company, AssetValues values) {
        if (company == null || values == null) throw invalid();
        this.company = company;
        replace(values);
    }

    public void replace(AssetValues values) {
        if (values == null) throw invalid();
        this.assetTag = values.assetTag();
        this.description = values.description();
        this.category = values.category();
        this.brandModel = values.brandModel();
        this.serialNumber = values.serialNumber();
        this.acquisitionDate = values.acquisitionDate();
        this.acquisitionValue = values.acquisitionValue();
        this.quantity = values.quantity();
        this.businessPartner = values.businessPartner();
        this.documentReference = values.documentReference();
        this.resultCenter = values.resultCenter();
        this.location = values.location();
        this.responsible = values.responsible();
        this.usefulLifeMonths = values.usefulLifeMonths();
        this.residualPercent = values.residualPercent();
        this.depreciable = values.depreciable();
        this.depreciationStartDate = values.depreciationStartDate();
        this.status = values.status();
        this.warrantyUntil = values.warrantyUntil();
        this.notes = values.notes();
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
    public String getAssetTag() { return assetTag; }
    public String getDescription() { return description; }
    public AssetCategory getCategory() { return category; }
    public String getBrandModel() { return brandModel; }
    public String getSerialNumber() { return serialNumber; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public BigDecimal getAcquisitionValue() { return acquisitionValue; }
    public int getQuantity() { return quantity; }
    public BusinessPartner getBusinessPartner() { return businessPartner; }
    public String getDocumentReference() { return documentReference; }
    public ResultCenter getResultCenter() { return resultCenter; }
    public String getLocation() { return location; }
    public String getResponsible() { return responsible; }
    public int getUsefulLifeMonths() { return usefulLifeMonths; }
    public BigDecimal getResidualPercent() { return residualPercent; }
    public boolean isDepreciable() { return depreciable; }
    public LocalDate getDepreciationStartDate() { return depreciationStartDate; }
    public AssetStatus getStatus() { return status; }
    public LocalDate getWarrantyUntil() { return warrantyUntil; }
    public String getNotes() { return notes; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    AssetDepreciationCalculator.Input depreciationInput() {
        return new AssetDepreciationCalculator.Input(
                acquisitionValue,
                usefulLifeMonths,
                residualPercent,
                depreciable,
                acquisitionDate,
                depreciationStartDate,
                null);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Bem patrimonial inválido.");
    }
}
