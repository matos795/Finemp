package com.finemp.access;

import com.finemp.TestcontainersConfiguration;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CompanyAccessGrantRepositoryIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyAccessGrantRepository companyAccessGrantRepository;

    @Test
    void shouldRejectDuplicateAccessGrantForSameUserAndCompany() {

        UserAccount userAccount = userAccountRepository.save(
                new UserAccount(
                        "Usuário Exemplo",
                        "usuario@example.com",
                        "hash-exemplo"));

        Company company = companyRepository.save(
                new Company(
                        "Empresa Exemplo Ltda",
                        "11111111000111"));

        companyAccessGrantRepository.saveAndFlush(
                new CompanyAccessGrant(userAccount, company));

        assertThatThrownBy(() -> companyAccessGrantRepository.saveAndFlush(
                new CompanyAccessGrant(userAccount, company)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowSameUserToAccessDifferentCompanies() {

        UserAccount userAccount = userAccountRepository.save(
                new UserAccount(
                        "Usuário Exemplo",
                        "usuario@example.com",
                        "hash-exemplo"));

        Company companyA = companyRepository.save(
                new Company(
                        "Empresa A Ltda",
                        "22222222000122"));

        Company companyB = companyRepository.save(
                new Company(
                        "Empresa B Ltda",
                        "33333333000133"));

        companyAccessGrantRepository.save(
                new CompanyAccessGrant(userAccount, companyA));

        companyAccessGrantRepository.save(
                new CompanyAccessGrant(userAccount, companyB));

        assertThat(companyAccessGrantRepository.count())
                .isEqualTo(2);
    }
}