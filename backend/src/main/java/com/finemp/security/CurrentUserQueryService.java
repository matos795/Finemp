package com.finemp.security;

import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Service
public class CurrentUserQueryService {

    private final UserAccountRepository userAccountRepository;
    private final CompanyAccessGrantRepository companyAccessGrantRepository;

    public CurrentUserQueryService(
            UserAccountRepository userAccountRepository,
            CompanyAccessGrantRepository companyAccessGrantRepository) {

        this.userAccountRepository = userAccountRepository;
        this.companyAccessGrantRepository = companyAccessGrantRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse findCurrentUser(
            UUID userAccountId) {

        UserAccount userAccount = userAccountRepository.findById(userAccountId)
                .orElseThrow();

        var companies = companyAccessGrantRepository
                .findAllByUserAccountId(userAccountId)
                .stream()
                .map(CurrentUserCompanyAccessResponse::from)
                .sorted(
                        Comparator.comparing(
                                CurrentUserCompanyAccessResponse::name))
                .toList();

        return new CurrentUserResponse(
                userAccount.getId(),
                userAccount.getName(),
                userAccount.getEmail(),
                companies);
    }
}