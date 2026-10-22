package com.finemp.access;

import com.finemp.company.Company;

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
@Table(name = "company_access_grants")
public class CompanyAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_role", nullable = false, length = 30)
    private CompanyAccessRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_center_scope", nullable = false, length = 20)
    private CostCenterScope costCenterScope;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_access_grant_permissions", joinColumns = @JoinColumn(name = "company_access_grant_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 80)
    private Set<CompanyPermission> permissions = new HashSet<>();

    protected CompanyAccessGrant() {
    }

    public CompanyAccessGrant(
            UserAccount userAccount,
            Company company) {

        this(
                userAccount,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of());
    }

    public CompanyAccessGrant(
            UserAccount userAccount,
            Company company,
            CompanyAccessRole role,
            CostCenterScope costCenterScope,
            Set<CompanyPermission> permissions) {

        this.userAccount = userAccount;
        this.company = company;
        this.role = role;
        this.costCenterScope = costCenterScope;
        this.permissions = new HashSet<>(permissions);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public Company getCompany() {
        return company;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CompanyAccessRole getRole() {
        return role;
    }

    public CostCenterScope getCostCenterScope() {
        return costCenterScope;
    }

    public Set<CompanyPermission> getPermissions() {
        return Set.copyOf(permissions);
    }

    public boolean hasPermission(CompanyPermission permission) {
        return permissions.contains(permission);
    }
}