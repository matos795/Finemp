package com.finemp.access;

import com.finemp.accountingfirm.AccountingFirm;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "accounting_firm_access_grants")
public class AccountingFirmAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_firm_id", nullable = false)
    private AccountingFirm accountingFirm;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "accounting_firm_access_grant_permissions",
            joinColumns = @JoinColumn(name = "accounting_firm_access_grant_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 80)
    private Set<AccountingFirmPermission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountingFirmAccessGrant() {
    }

    public AccountingFirmAccessGrant(
            UserAccount userAccount,
            AccountingFirm accountingFirm,
            Set<AccountingFirmPermission> permissions) {
        this.userAccount = userAccount;
        this.accountingFirm = accountingFirm;
        this.permissions = new HashSet<>(permissions);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public AccountingFirm getAccountingFirm() {
        return accountingFirm;
    }

    public Set<AccountingFirmPermission> getPermissions() {
        return Set.copyOf(permissions);
    }

    public void addPermissions(Set<AccountingFirmPermission> permissions) {
        this.permissions.addAll(permissions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
