package com.finemp.accounting.canonical.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounting_daily_debit_credit_totals")
public class AccountingDailyDebitCreditTotals {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "accounting_source_id", nullable = false, updatable = false)
    private UUID accountingSourceId;
    @Column(name = "establishment_code", nullable = false, updatable = false)
    private String establishmentCode;
    @Column(name = "source_account_id", nullable = false, updatable = false)
    private String sourceAccountId;
    @Column(name = "day", nullable = false, updatable = false)
    private LocalDate day;
    @Column(name = "entry_type_code", nullable = false, updatable = false)
    private String entryTypeCode;
    @Column(name = "debit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitTotal;
    @Column(name = "credit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditTotal;

    protected AccountingDailyDebitCreditTotals() {}

    AccountingDailyDebitCreditTotals(
            UUID accountingSourceId,
            String establishmentCode,
            String sourceAccountId,
            LocalDate day,
            String entryTypeCode,
            BigDecimal debitTotal,
            BigDecimal creditTotal) {
        this.accountingSourceId = accountingSourceId;
        this.establishmentCode = establishmentCode;
        this.sourceAccountId = sourceAccountId;
        this.day = day;
        this.entryTypeCode = entryTypeCode;
        this.debitTotal = debitTotal;
        this.creditTotal = creditTotal;
    }

    public UUID getId() { return id; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public String getEstablishmentCode() { return establishmentCode; }
    public String getSourceAccountId() { return sourceAccountId; }
    public LocalDate getDay() { return day; }
    public String getEntryTypeCode() { return entryTypeCode; }
    public BigDecimal getDebitTotal() { return debitTotal; }
    public BigDecimal getCreditTotal() { return creditTotal; }
}
