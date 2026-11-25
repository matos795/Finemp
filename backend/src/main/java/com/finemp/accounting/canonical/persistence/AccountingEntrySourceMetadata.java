package com.finemp.accounting.canonical.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounting_entry_source_metadata")
public class AccountingEntrySourceMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "accounting_source_id", nullable = false, updatable = false)
    private UUID accountingSourceId;

    @Column(name = "source_entry_key", nullable = false, updatable = false)
    private String sourceEntryKey;

    @Column(name = "source_recorded_at")
    private Instant sourceRecordedAt;
    @Column(name = "history_code")
    private String historyCode;
    @Column(name = "origin_code")
    private String originCode;
    @Column(name = "entry_type_code")
    private String entryTypeCode;
    @Column(name = "data_origin_code")
    private String dataOriginCode;
    @Column(name = "batch_code")
    private String batchCode;
    @Column(name = "document_number")
    private String documentNumber;
    @Column(name = "operation_group_key")
    private String operationGroupKey;
    @Column(name = "transaction_key")
    private String transactionKey;
    @Column(name = "origin_establishment_code")
    private String originEstablishmentCode;

    protected AccountingEntrySourceMetadata() {}

    AccountingEntrySourceMetadata(
            UUID accountingSourceId,
            String sourceEntryKey,
            Instant sourceRecordedAt,
            String historyCode,
            String originCode,
            String entryTypeCode,
            String dataOriginCode,
            String batchCode,
            String documentNumber,
            String operationGroupKey,
            String transactionKey,
            String originEstablishmentCode) {
        this.accountingSourceId = accountingSourceId;
        this.sourceEntryKey = sourceEntryKey;
        this.sourceRecordedAt = sourceRecordedAt;
        this.historyCode = historyCode;
        this.originCode = originCode;
        this.entryTypeCode = entryTypeCode;
        this.dataOriginCode = dataOriginCode;
        this.batchCode = batchCode;
        this.documentNumber = documentNumber;
        this.operationGroupKey = operationGroupKey;
        this.transactionKey = transactionKey;
        this.originEstablishmentCode = originEstablishmentCode;
    }

    public UUID getId() { return id; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public String getSourceEntryKey() { return sourceEntryKey; }
    public Instant getSourceRecordedAt() { return sourceRecordedAt; }
    public String getHistoryCode() { return historyCode; }
    public String getOriginCode() { return originCode; }
    public String getEntryTypeCode() { return entryTypeCode; }
    public String getDataOriginCode() { return dataOriginCode; }
    public String getBatchCode() { return batchCode; }
    public String getDocumentNumber() { return documentNumber; }
    public String getOperationGroupKey() { return operationGroupKey; }
    public String getTransactionKey() { return transactionKey; }
    public String getOriginEstablishmentCode() { return originEstablishmentCode; }
}
