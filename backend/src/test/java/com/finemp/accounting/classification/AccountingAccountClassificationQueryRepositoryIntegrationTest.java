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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingAccountClassificationQueryRepositoryIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private DreClassificationRepository dreClassificationRepository;

    @Autowired
    private AccountingAccountClassificationRepository classificationRepository;

    @Autowired
    private AccountingAccountClassificationQueryRepository queryRepository;

    @Test
    void shouldReturnClassifiedAndPendingAccountsOnlyFromRequestedCompany() {

        Company companyA = companyRepository.save(
                new Company(
                        "Empresa A Ltda",
                        "13131313000113"));

        Company companyB = companyRepository.save(
                new Company(
                        "Empresa B Ltda",
                        "14141414000114"));

        AccountingSource sourceA = accountingSourceRepository.save(
                new AccountingSource(
                        companyA,
                        AccountingSourceType.QUESTOR,
                        "company-a-query",
                        true));

        AccountingSource sourceB = accountingSourceRepository.save(
                new AccountingSource(
                        companyB,
                        AccountingSourceType.QUESTOR,
                        "company-b-query",
                        true));

        AccountingAccount classifiedAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        sourceA,
                        "100",
                        "3.1.01.100",
                        "Conta classificada"));

        AccountingAccount pendingAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        sourceA,
                        "200",
                        "4.1.01.200",
                        "Conta pendente"));

        accountingAccountRepository.save(
                new AccountingAccount(
                        sourceB,
                        "300",
                        "3.1.01.300",
                        "Conta de outra empresa"));

        DreClassification classification = dreClassificationRepository.save(
                new DreClassification(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"));

        classificationRepository.save(
                new AccountingAccountClassification(
                        classifiedAccount,
                        classification));

        List<AccountingAccountClassificationRow> result = queryRepository.findAllByCompanyId(
                companyA.getId());

        assertThat(result)
                .hasSize(2);

        AccountingAccountClassificationRow classified = result.stream()
                .filter(row -> row.accountId()
                        .equals(classifiedAccount.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(classified.classificationId())
                .isEqualTo(classification.getId());

        assertThat(classified.dreClassification())
                .isEqualTo("EBITDA");

        AccountingAccountClassificationRow pending = result.stream()
                .filter(row -> row.accountId()
                        .equals(pendingAccount.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(pending.classificationId())
                .isNull();

        assertThat(pending.dreClassification())
                .isNull();
    }
}