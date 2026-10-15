package com.finemp.accounting.classification;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingAccountClassificationServiceIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private AccountingAccountClassificationRepository classificationRepository;

    @Autowired
    private AccountingAccountClassificationService classificationService;

    @Test
    void shouldPersistClassificationForExistingAccountingAccount() {

        Company company = companyRepository.save(
                new Company(
                        "Empresa Exemplo Ltda",
                        "77777777000177"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "company-example",
                        true));

        AccountingAccount account = accountingAccountRepository.save(
                new AccountingAccount(
                        source,
                        "100",
                        "1.1.01.100",
                        "Conta exemplo"));

        classificationService.classifyAccount(
                account.getId(),
                "EBITDA",
                "RECEITA BRUTA",
                "LOCAÇÃO");

        AccountingAccountClassification classification = classificationRepository
                .findByAccountingAccountId(account.getId())
                .orElseThrow();

        assertThat(classification.getAccountingAccount().getId())
                .isEqualTo(account.getId());

        assertThat(classification.getDreClassification())
                .isEqualTo("EBITDA");

        assertThat(classification.getDreOpenClassification())
                .isEqualTo("RECEITA BRUTA");

        assertThat(classification.getEbitdaClassification())
                .isEqualTo("LOCAÇÃO");
    }
}