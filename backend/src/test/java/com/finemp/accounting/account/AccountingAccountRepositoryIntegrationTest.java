package com.finemp.accounting.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
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
class AccountingAccountRepositoryIntegrationTest {

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAccountingAccountForSource() {
        Company company = companyRepository.save(
                new Company("Empresa Exemplo Ltda", "12345678000190"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "company-ref",
                        true));

        AccountingAccount account = new AccountingAccount(
                source,
                "5396",
                "3.1.01.005.001.009.5396",
                "Receita de Exemplo");

        AccountingAccount savedAccount = accountingAccountRepository.save(account);

        entityManager.flush();
        entityManager.clear();

        AccountingAccount retrieved = accountingAccountRepository.findById(savedAccount.getId()).orElseThrow();

        assertThat(retrieved.getExternalAccountId()).isEqualTo("5396");
        assertThat(retrieved.getAccountKey()).isEqualTo("3.1.01.005.001.009.5396");
        assertThat(retrieved.getDescription()).isEqualTo("Receita de Exemplo");
        assertThat(retrieved.getAccountingSource().getId()).isEqualTo(source.getId());
    }

    @Test
    void shouldAllowSameAccountKeyForDifferentSources() {
        Company firstCompany = companyRepository.save(
                new Company("Empresa Um Ltda", "11111111000111"));

        Company secondCompany = companyRepository.save(
                new Company("Empresa Dois Ltda", "22222222000122"));

        AccountingSource firstSource = accountingSourceRepository.save(
                new AccountingSource(
                        firstCompany,
                        AccountingSourceType.QUESTOR,
                        "company-1",
                        true));

        AccountingSource secondSource = accountingSourceRepository.save(
                new AccountingSource(
                        secondCompany,
                        AccountingSourceType.QUESTOR,
                        "company-2",
                        true));

        accountingAccountRepository.save(
                new AccountingAccount(
                        firstSource,
                        "100",
                        "3.1.01.001",
                        "Receita Empresa Um"));

        accountingAccountRepository.save(
                new AccountingAccount(
                        secondSource,
                        "200",
                        "3.1.01.001",
                        "Receita Empresa Dois"));

        entityManager.flush();

        assertThat(accountingAccountRepository.count()).isEqualTo(2);
    }
}