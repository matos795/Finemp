package com.finemp.security;

import com.finemp.access.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionAccountStateValidator {

    private final UserAccountRepository repository;

    public SessionAccountStateValidator(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isCurrent(UserAccountPrincipal principal) {
        return repository.existsByIdAndActiveTrueAndCredentialsVersion(
                principal.getUserAccountId(), principal.getCredentialsVersion());
    }
}
