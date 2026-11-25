package com.finemp.accounting.canonical.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "accounting_origin_catalog")
public class AccountingOriginCatalogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "accounting_source_id", nullable = false, updatable = false)
    private UUID accountingSourceId;
    @Column(name = "origin_code", nullable = false, updatable = false)
    private String originCode;
    @Column(name = "description", nullable = false)
    private String description;

    protected AccountingOriginCatalogEntry() {}

    AccountingOriginCatalogEntry(UUID accountingSourceId, String originCode, String description) {
        this.accountingSourceId = accountingSourceId;
        this.originCode = originCode;
        this.description = description;
    }

    public UUID getId() { return id; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public String getOriginCode() { return originCode; }
    public String getDescription() { return description; }
}
