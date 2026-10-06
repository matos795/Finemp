package com.finemp.accounting.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingSourceRepositoryIntegrationTest {

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAccountingSourceForCompany() {
        Company company = companyRepository.save(new Company("Empresa Exemplo Ltda", "12345678000190"));

        AccountingSource source = new AccountingSource(
                company,
                AccountingSourceType.QUESTOR,
                "57",
                true);

        AccountingSource savedSource = accountingSourceRepository.save(source);

        entityManager.flush();
        entityManager.clear();

        AccountingSource retrieved = accountingSourceRepository.findById(savedSource.getId()).orElseThrow();

        assertThat(retrieved.getType()).isEqualTo(AccountingSourceType.QUESTOR);
        assertThat(retrieved.getExternalReference()).isEqualTo("57");
        assertThat(retrieved.isActive()).isTrue();
        assertThat(retrieved.getCompany().getId()).isEqualTo(company.getId());
    }

    @Test
    void shouldAllowMultipleAccountingSourcesForSameCompany() {
        Company company = companyRepository.save(
                new Company("Empresa Exemplo Ltda", "12345678000190"));

        accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "57",
                        true));

        accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "58",
                        false));

        entityManager.flush();

        assertThat(accountingSourceRepository.count()).isEqualTo(2);
    }
}