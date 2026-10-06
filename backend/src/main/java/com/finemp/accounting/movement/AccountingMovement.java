package com.finemp.accounting.movement;

import com.finemp.accounting.account.AccountingAccount;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounting_movements")
public class AccountingMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountingAccount account;

    @Column(name = "source_entry_key", nullable = false)
    private String sourceEntryKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private AccountingMovementSide side;

    @Column(name = "accounting_date", nullable = false)
    private LocalDate accountingDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "history")
    private String history;

    protected AccountingMovement() {
    }

    public AccountingMovement(
            AccountingAccount account,
            String sourceEntryKey,
            AccountingMovementSide side,
            LocalDate accountingDate,
            BigDecimal amount,
            String history) {
        this.account = account;
        this.sourceEntryKey = sourceEntryKey;
        this.side = side;
        this.accountingDate = accountingDate;
        this.amount = amount;
        this.history = history;
    }

    public UUID getId() {
        return id;
    }

    public AccountingAccount getAccount() {
        return account;
    }

    public String getSourceEntryKey() {
        return sourceEntryKey;
    }

    public AccountingMovementSide getSide() {
        return side;
    }

    public LocalDate getAccountingDate() {
        return accountingDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getHistory() {
        return history;
    }
}