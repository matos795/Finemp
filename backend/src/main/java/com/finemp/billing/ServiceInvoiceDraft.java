package com.finemp.billing;

import com.finemp.company.Company;
import com.finemp.contracts.Contract;
import com.finemp.finance.partner.BusinessPartner;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_invoice_drafts")
public class ServiceInvoiceDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_partner_id", nullable = false)
    private BusinessPartner businessPartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Embedded
    private ServiceInvoiceDraftValues values;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceInvoiceStatus status;

    @Column(name = "invoice_number", length = 80)
    private String invoiceNumber;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceInvoiceDraft() {
    }

    public ServiceInvoiceDraft(
            Company company,
            BusinessPartner businessPartner,
            Contract contract,
            ServiceInvoiceDraftValues values) {
        if (company == null || businessPartner == null || values == null) throw invalid();
        this.company = company;
        this.businessPartner = businessPartner;
        this.contract = contract;
        this.values = values;
        this.status = ServiceInvoiceStatus.DRAFT;
    }

    public void replace(
            BusinessPartner businessPartner,
            Contract contract,
            ServiceInvoiceDraftValues values) {
        requireDraft();
        if (businessPartner == null || values == null) throw invalid();
        this.businessPartner = businessPartner;
        this.contract = contract;
        this.values = values;
    }

    public void markIssued(String invoiceNumber) {
        requireDraft();
        String number = invoiceNumber == null ? "" : invoiceNumber.trim();
        if (number.isEmpty() || number.length() > 80) throw invalid();
        this.status = ServiceInvoiceStatus.ISSUED;
        this.invoiceNumber = number;
    }

    public void cancel() {
        if (status == ServiceInvoiceStatus.CANCELED) return;
        this.status = ServiceInvoiceStatus.CANCELED;
        this.invoiceNumber = null;
    }

    private void requireDraft() {
        if (status != ServiceInvoiceStatus.DRAFT) {
            throw new IllegalStateException("Somente rascunho pode ser alterado.");
        }
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
    public Contract getContract() { return contract; }
    public ServiceInvoiceDraftValues getValues() { return values; }
    public ServiceInvoiceStatus getStatus() { return status; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Estado da nota fiscal inválido.");
    }
}
