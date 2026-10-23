package com.finemp.accounting.account;

import com.finemp.accounting.source.AccountingSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "accounting_accounts")
public class AccountingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_source_id", nullable = false)
    private AccountingSource accountingSource;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "account_key", nullable = false)
    private String accountKey;

    @Column(name = "description", nullable = false)
    private String description;

    protected AccountingAccount() {
    }

    public AccountingAccount(
            AccountingSource accountingSource,
            String externalAccountId,
            String accountKey,
            String description) {
        this.accountingSource = accountingSource;
        this.externalAccountId = externalAccountId;
        this.accountKey = accountKey;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public AccountingSource getAccountingSource() {
        return accountingSource;
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getDescription() {
        return description;
    }

    public void synchronizeMetadata(
            String accountKey,
            String description) {

        this.accountKey = accountKey;
        this.description = description;
    }
}