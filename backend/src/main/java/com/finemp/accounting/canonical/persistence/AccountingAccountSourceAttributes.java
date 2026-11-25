package com.finemp.accounting.canonical.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "accounting_account_source_attributes")
public class AccountingAccountSourceAttributes {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "accounting_source_id", nullable = false, updatable = false)
    private UUID accountingSourceId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private String sourceAccountId;

    @Column(name = "classification_code", nullable = false)
    private String classificationCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "calculation_type", nullable = false)
    private String calculationType;

    @Column(name = "balance_nature", nullable = false)
    private String balanceNature;

    @Column(name = "balance_control", nullable = false)
    private String balanceControl;

    @Column(name = "show_in_balance_sheet", nullable = false)
    private String showInBalanceSheet;

    @Column(name = "native_dre_code")
    private String nativeDreCode;

    @Column(name = "native_dre_account")
    private String nativeDreAccount;

    protected AccountingAccountSourceAttributes() {}

    AccountingAccountSourceAttributes(
            UUID accountingSourceId,
            String sourceAccountId,
            String classificationCode,
            String description,
            String accountType,
            String calculationType,
            String balanceNature,
            String balanceControl,
            String showInBalanceSheet,
            String nativeDreCode,
            String nativeDreAccount) {
        this.accountingSourceId = accountingSourceId;
        this.sourceAccountId = sourceAccountId;
        this.classificationCode = classificationCode;
        this.description = description;
        this.accountType = accountType;
        this.calculationType = calculationType;
        this.balanceNature = balanceNature;
        this.balanceControl = balanceControl;
        this.showInBalanceSheet = showInBalanceSheet;
        this.nativeDreCode = nativeDreCode;
        this.nativeDreAccount = nativeDreAccount;
    }

    public UUID getId() { return id; }
    public UUID getAccountingSourceId() { return accountingSourceId; }
    public String getSourceAccountId() { return sourceAccountId; }
    public String getClassificationCode() { return classificationCode; }
    public String getDescription() { return description; }
    public String getAccountType() { return accountType; }
    public String getCalculationType() { return calculationType; }
    public String getBalanceNature() { return balanceNature; }
    public String getBalanceControl() { return balanceControl; }
    public String getShowInBalanceSheet() { return showInBalanceSheet; }
    public String getNativeDreCode() { return nativeDreCode; }
    public String getNativeDreAccount() { return nativeDreAccount; }
}
