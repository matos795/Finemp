package com.finemp.security;

import com.finemp.access.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserAccountPrincipal implements UserDetails {

    private final UUID userAccountId;
    private final String email;
    private final String passwordHash;
    private final boolean active;

    public UserAccountPrincipal(UserAccount userAccount) {
        this.userAccountId = userAccount.getId();
        this.email = userAccount.getEmail();
        this.passwordHash = userAccount.getPasswordHash();
        this.active = userAccount.isActive();
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}