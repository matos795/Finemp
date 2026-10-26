package com.finemp.client;

import com.finemp.accountingfirm.AccountingFirm;
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
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_firm_id", nullable = false)
    private AccountingFirm accountingFirm;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Client() {
    }

    public Client(
            AccountingFirm accountingFirm,
            String name,
            boolean active) {
        this.accountingFirm = accountingFirm;
        this.name = name;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public AccountingFirm getAccountingFirm() {
        return accountingFirm;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}

