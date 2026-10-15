package com.finemp.accounting.classification;

import com.finemp.accounting.account.AccountingAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "accounting_account_classifications")
public class AccountingAccountClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_account_id", nullable = false, unique = true)
    private AccountingAccount accountingAccount;

    @Column(name = "dre_classification")
    private String dreClassification;

    @Column(name = "dre_open_classification")
    private String dreOpenClassification;

    @Column(name = "ebitda_classification")
    private String ebitdaClassification;

    protected AccountingAccountClassification() {
    }

    public AccountingAccountClassification(
            AccountingAccount accountingAccount,
            String dreClassification,
            String dreOpenClassification,
            String ebitdaClassification) {
        this.accountingAccount = accountingAccount;
        this.dreClassification = dreClassification;
        this.dreOpenClassification = dreOpenClassification;
        this.ebitdaClassification = ebitdaClassification;
    }

    public UUID getId() {
        return id;
    }

    public AccountingAccount getAccountingAccount() {
        return accountingAccount;
    }

    public String getDreClassification() {
        return dreClassification;
    }

    public String getDreOpenClassification() {
        return dreOpenClassification;
    }

    public String getEbitdaClassification() {
        return ebitdaClassification;
    }
}