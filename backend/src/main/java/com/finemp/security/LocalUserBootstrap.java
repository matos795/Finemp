package com.finemp.security;

import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.UUID;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "finemp.local-user", name = "enabled", havingValue = "true")
public class LocalUserBootstrap implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAccessGrantRepository companyAccessGrantRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${finemp.local-user.name:Usuário Local}")
    private String name;

    @Value("${finemp.local-user.email}")
    private String email;

    @Value("${finemp.local-user.password}")
    private String password;

    @Value("${finemp.local-user.company-id}")
    private UUID companyId;

    public LocalUserBootstrap(
            UserAccountRepository userAccountRepository,
            CompanyRepository companyRepository,
            CompanyAccessGrantRepository companyAccessGrantRepository,
            PasswordEncoder passwordEncoder) {

        this.userAccountRepository = userAccountRepository;
        this.companyRepository = companyRepository;
        this.companyAccessGrantRepository = companyAccessGrantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (userAccountRepository
                .findByEmailIgnoreCase(email)
                .isPresent()) {
            return;
        }

        Company company = companyRepository
                .findById(companyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Local user company does not exist"));

        UserAccount user = userAccountRepository.saveAndFlush(
                new UserAccount(
                        name,
                        email,
                        passwordEncoder.encode(password)));

        companyAccessGrantRepository.saveAndFlush(
                new CompanyAccessGrant(
                        user,
                        company,
                        CompanyAccessRole.ADMIN,
                        CostCenterScope.ALL,
                        EnumSet.allOf(CompanyPermission.class)));
    }
}