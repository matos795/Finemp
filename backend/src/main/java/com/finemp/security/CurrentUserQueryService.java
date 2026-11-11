package com.finemp.security;

import com.finemp.access.*;
import com.finemp.company.CompanyRepository;
import org.springframework.data.domain.Sort;
import java.util.EnumSet;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Service
public class CurrentUserQueryService {

    private final CompanyRepository companies;
    private final UserAccountRepository userAccountRepository;
    private final CompanyAccessGrantRepository companyAccessGrantRepository;

    public CurrentUserQueryService(
            UserAccountRepository userAccountRepository,
            CompanyAccessGrantRepository companyAccessGrantRepository, CompanyRepository companies) {
        this.companies = companies;

        this.userAccountRepository = userAccountRepository;
        this.companyAccessGrantRepository = companyAccessGrantRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse findCurrentUser(
            UUID userAccountId) {

        UserAccount userAccount = userAccountRepository.findById(userAccountId)
                .orElseThrow();

        var platformPermissions = userAccount.getPlatformPermissions()
                .stream()
                .sorted()
                .toList();

        boolean global = userAccount.isActive() && platformPermissions.contains(PlatformPermission.SYSTEM_ADMIN);
        var accesses = global
                ? companies.findAll(Sort.by("legalName", "id")).stream()
                        .map(company -> new CurrentUserCompanyAccessResponse(company.getId(), company.getLegalName(),
                                CompanyAccessRole.ADMIN, EnumSet.allOf(CompanyPermission.class), CostCenterScope.ALL))
                        .toList()
                : companyAccessGrantRepository
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
                platformPermissions,
                accesses);
    }
}
