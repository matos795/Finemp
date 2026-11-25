package com.finemp.accounting.coverage;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import jakarta.persistence.*;
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

    @Column(name = "imported_from", nullable = false, updatable = false)
    private LocalDate importedFrom;

    @Column(name = "imported_to", nullable = false, updatable = false)
    private LocalDate importedTo;

    protected AccountingDatasetCoverage() {}

    AccountingDatasetCoverage(
            UUID accountingSourceId,
            AccountingDataset dataset,
            LocalDate importedFrom,
            LocalDate importedTo) {
        this.accountingSourceId = accountingSourceId;
        this.dataset = dataset;
        this.importedFrom = importedFrom;
        this.importedTo = importedTo;
    }

    public UUID getId() { return id; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public AccountingDataset getDataset() { return dataset; }
    public LocalDate getImportedFrom() { return importedFrom; }
    public LocalDate getImportedTo() { return importedTo; }
}
