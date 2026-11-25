package com.finemp.accounting.coverage;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounting_dataset_coverage")
public class AccountingDatasetCoverage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "accounting_source_id", nullable = false, updatable = false)
    private UUID accountingSourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dataset", nullable = false, updatable = false)
    private AccountingDataset dataset;

    @Column(name = "establishment_code", updatable = false)
    private String establishmentCode;

    @Column(name = "imported_from", updatable = false)
    private LocalDate importedFrom;

    @Column(name = "imported_to", updatable = false)
    private LocalDate importedTo;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected AccountingDatasetCoverage() {}

    AccountingDatasetCoverage(
            UUID accountingSourceId,
            AccountingDataset dataset,
            LocalDate importedFrom,
            LocalDate importedTo) {
        this(accountingSourceId, dataset, null, importedFrom, importedTo, null);
    }

    AccountingDatasetCoverage(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode,
            LocalDate importedFrom,
            LocalDate importedTo,
            Instant publishedAt) {
        this.accountingSourceId = accountingSourceId;
        this.dataset = dataset;
        this.establishmentCode = establishmentCode;
        this.importedFrom = importedFrom;
        this.importedTo = importedTo;
        this.publishedAt = publishedAt;
    }

    public UUID getId() { return id; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public AccountingDataset getDataset() { return dataset; }
    public String getEstablishmentCode() { return establishmentCode; }
    public LocalDate getImportedFrom() { return importedFrom; }
    public LocalDate getImportedTo() { return importedTo; }
    public Instant getPublishedAt() { return publishedAt; }
}
