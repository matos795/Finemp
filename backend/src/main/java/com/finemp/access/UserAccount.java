package com.finemp.access;

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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_account_platform_permissions",
            joinColumns = @JoinColumn(name = "user_account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 80)
    private Set<PlatformPermission> platformPermissions = new HashSet<>();

    @Column(name = "invitation_pending", nullable = false)
    private boolean invitationPending;

    @Column(name = "credentials_version", nullable = false)
    private long credentialsVersion;

    protected UserAccount() {
    }

    public UserAccount(
            String name,
            String email,
            String passwordHash) {

        this(name, email, passwordHash, Set.of());
    }

    public UserAccount(
            String name,
            String email,
            String passwordHash,
            Set<PlatformPermission> platformPermissions) {

        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.platformPermissions = new HashSet<>(platformPermissions);
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<PlatformPermission> getPlatformPermissions() {
        return Set.copyOf(platformPermissions);
    }

    public void deactivate() {
        this.active = false;
        this.invitationPending = false;
        this.credentialsVersion++;
    }

    public static UserAccount invited(String name, String email) {
        UserAccount userAccount = new UserAccount(name, email, "!pending-invitation");
        userAccount.active = false;
        userAccount.invitationPending = true;
        return userAccount;
    }

    public boolean isInvitationPending() {
        return invitationPending;
    }

    public long getCredentialsVersion() {
        return credentialsVersion;
    }

    public void completeInvitation(String passwordHash) {
        this.passwordHash = passwordHash;
        this.invitationPending = false;
        this.active = true;
        this.credentialsVersion++;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.credentialsVersion++;
    }

    public void reactivate() {
        if (invitationPending || "!pending-invitation".equals(passwordHash)) {
            throw new IllegalStateException("Account must define its own password before administrative reactivation.");
        }
        if (!active) {
            this.active = true;
            this.credentialsVersion++;
        }
    }

    public void replacePlatformPermissions(Set<PlatformPermission> permissions) {
        Set<PlatformPermission> requested = Set.copyOf(permissions);
        this.platformPermissions.clear();
        this.platformPermissions.addAll(requested);
    }
}
