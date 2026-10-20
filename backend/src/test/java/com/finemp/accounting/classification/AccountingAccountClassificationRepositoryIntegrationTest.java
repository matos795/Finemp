package com.finemp.accounting.classification;

import static org.assertj.core.api.Assertions.assertThat;

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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingAccountClassificationRepositoryIntegrationTest {

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private AccountingSourceRepository accountingSourceRepository;

        @Autowired
        private AccountingAccountRepository accountingAccountRepository;

        @Autowired
        private AccountingAccountClassificationRepository classificationRepository;

        @Autowired
        private DreClassificationRepository dreClassificationRepository;

        @Test
        void shouldKeepClassificationScopedToItsAccountingAccount() {

                Company companyA = companyRepository.save(
                                new Company(
                                                "Empresa A Ltda",
                                                "11111111000111"));

                Company companyB = companyRepository.save(
                                new Company(
                                                "Empresa B Ltda",
                                                "22222222000122"));

                AccountingSource sourceA = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyA,
                                                AccountingSourceType.QUESTOR,
                                                "company-a",
                                                true));

                AccountingSource sourceB = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyB,
                                                AccountingSourceType.QUESTOR,
                                                "company-b",
                                                true));

                AccountingAccount accountA = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceA,
                                                "100",
                                                "1.1.01.100",
                                                "Conta exemplo A"));

                AccountingAccount accountB = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceB,
                                                "100",
                                                "1.1.01.100",
                                                "Conta exemplo B"));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                accountA,
                                                "EBITDA",
                                                "RECEITA",
                                                "GRUPO A"));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                accountB,
                                                "FINANCEIRAS",
                                                "RECEITA FINANCEIRA",
                                                "GRUPO B"));

                AccountingAccountClassification classificationA = classificationRepository
                                .findByAccountingAccountId(
                                                accountA.getId())
                                .orElseThrow();

                AccountingAccountClassification classificationB = classificationRepository
                                .findByAccountingAccountId(
                                                accountB.getId())
                                .orElseThrow();

                assertThat(classificationA.getDreClassification())
                                .isEqualTo("EBITDA");

                assertThat(classificationB.getDreClassification())
                                .isEqualTo("FINANCEIRAS");

                assertThat(classificationRepository.count())
                                .isEqualTo(2);
        }

        @Test
        void shouldPersistClassificationMarkersWithoutInterpretingThem() {

                Company company = companyRepository.save(
                                new Company(
                                                "Empresa Exemplo Ltda",
                                                "33333333000133"));

                AccountingSource source = accountingSourceRepository.save(
                                new AccountingSource(
                                                company,
                                                AccountingSourceType.QUESTOR,
                                                "company-example",
                                                true));

                AccountingAccount account = accountingAccountRepository.save(
                                new AccountingAccount(
                                                source,
                                                "300",
                                                "3.1.01.300",
                                                "Conta exemplo"));

                AccountingAccountClassification saved = classificationRepository.save(
                                new AccountingAccountClassification(
                                                account,
                                                "0",
                                                "(-) Verificar",
                                                "N/A"));

                AccountingAccountClassification classification = classificationRepository
                                .findById(saved.getId())
                                .orElseThrow();

                assertThat(classification.getDreClassification())
                                .isEqualTo("0");

                assertThat(classification.getDreOpenClassification())
                                .isEqualTo("(-) Verificar");

                assertThat(classification.getEbitdaClassification())
                                .isEqualTo("N/A");
        }

        @Test
        void shouldFindAccountingAccountsWithoutClassification() {

                Company company = companyRepository.save(
                                new Company(
                                                "Empresa Exemplo Ltda",
                                                "44444444000144"));

                AccountingSource source = accountingSourceRepository.save(
                                new AccountingSource(
                                                company,
                                                AccountingSourceType.QUESTOR,
                                                "company-example",
                                                true));

                AccountingAccount classifiedAccount = accountingAccountRepository.save(
                                new AccountingAccount(
                                                source,
                                                "100",
                                                "1.1.01.100",
                                                "Conta classificada"));

                AccountingAccount unclassifiedAccount = accountingAccountRepository.save(
                                new AccountingAccount(
                                                source,
                                                "200",
                                                "1.1.01.200",
                                                "Conta sem classificação"));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                classifiedAccount,
                                                "EBITDA",
                                                "RECEITA",
                                                "GRUPO A"));

                var unclassifiedAccounts = accountingAccountRepository
                                .findAllWithoutClassificationByAccountingSourceId(
                                                source.getId());

                assertThat(unclassifiedAccounts)
                                .extracting(AccountingAccount::getId)
                                .contains(unclassifiedAccount.getId())
                                .doesNotContain(classifiedAccount.getId());
        }

        @Test
        void shouldFindOnlyUnclassifiedAccountsFromRequestedAccountingSource() {

                Company companyA = companyRepository.save(
                                new Company(
                                                "Empresa A Ltda",
                                                "55555555000155"));

                Company companyB = companyRepository.save(
                                new Company(
                                                "Empresa B Ltda",
                                                "66666666000166"));

                AccountingSource sourceA = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyA,
                                                AccountingSourceType.QUESTOR,
                                                "company-a",
                                                true));

                AccountingSource sourceB = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyB,
                                                AccountingSourceType.QUESTOR,
                                                "company-b",
                                                true));

                AccountingAccount classifiedAccountA = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceA,
                                                "100",
                                                "1.1.01.100",
                                                "Conta classificada A"));

                AccountingAccount unclassifiedAccountA = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceA,
                                                "200",
                                                "1.1.01.200",
                                                "Conta pendente A"));

                AccountingAccount unclassifiedAccountB = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceB,
                                                "200",
                                                "1.1.01.200",
                                                "Conta pendente B"));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                classifiedAccountA,
                                                "EBITDA",
                                                "RECEITA",
                                                "GRUPO A"));

                var result = accountingAccountRepository
                                .findAllWithoutClassificationByAccountingSourceId(
                                                sourceA.getId());

                assertThat(result)
                                .extracting(AccountingAccount::getId)
                                .containsExactly(unclassifiedAccountA.getId())
                                .doesNotContain(unclassifiedAccountB.getId());
        }

        @Test
        void shouldAllowAccountsFromDifferentCompaniesToReuseSameGlobalClassification() {

                Company companyA = companyRepository.save(
                                new Company(
                                                "Empresa A Ltda",
                                                "77777777000177"));

                Company companyB = companyRepository.save(
                                new Company(
                                                "Empresa B Ltda",
                                                "88888888000188"));

                AccountingSource sourceA = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyA,
                                                AccountingSourceType.QUESTOR,
                                                "company-a-global-classification",
                                                true));

                AccountingSource sourceB = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyB,
                                                AccountingSourceType.QUESTOR,
                                                "company-b-global-classification",
                                                true));

                AccountingAccount accountA = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceA,
                                                "100",
                                                "3.1.01.100",
                                                "Receita A"));

                AccountingAccount accountB = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceB,
                                                "200",
                                                "3.1.01.200",
                                                "Receita B"));

                DreClassification globalClassification = dreClassificationRepository.save(
                                new DreClassification(
                                                "EBITDA",
                                                "RECEITA BRUTA",
                                                "SERVIÇOS"));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                accountA,
                                                globalClassification));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                accountB,
                                                globalClassification));

                AccountingAccountClassification classificationA = classificationRepository
                                .findByAccountingAccountId(accountA.getId())
                                .orElseThrow();

                AccountingAccountClassification classificationB = classificationRepository
                                .findByAccountingAccountId(accountB.getId())
                                .orElseThrow();

                assertThat(classificationA.getClassification().getId())
                                .isEqualTo(globalClassification.getId());

                assertThat(classificationB.getClassification().getId())
                                .isEqualTo(globalClassification.getId());
        }

        @Test
        void shouldAllowSameAccountKeyInDifferentCompaniesToUseDifferentClassifications() {

                Company companyA = companyRepository.save(
                                new Company(
                                                "Empresa A Ltda",
                                                "99999999000199"));

                Company companyB = companyRepository.save(
                                new Company(
                                                "Empresa B Ltda",
                                                "10101010000110"));

                AccountingSource sourceA = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyA,
                                                AccountingSourceType.QUESTOR,
                                                "company-a-exception",
                                                true));

                AccountingSource sourceB = accountingSourceRepository.save(
                                new AccountingSource(
                                                companyB,
                                                AccountingSourceType.QUESTOR,
                                                "company-b-exception",
                                                true));

                AccountingAccount accountA = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceA,
                                                "100",
                                                "3.1.01.100",
                                                "Conta A"));

                AccountingAccount accountB = accountingAccountRepository.save(
                                new AccountingAccount(
                                                sourceB,
                                                "100",
                                                "3.1.01.100",
                                                "Conta B"));

                DreClassification servicesClassification = dreClassificationRepository.save(
                                new DreClassification(
                                                "EBITDA",
                                                "RECEITA BRUTA",
                                                "SERVIÇOS"));

                DreClassification otherRevenueClassification = dreClassificationRepository.save(
                                new DreClassification(
                                                "EBITDA",
                                                "OUTRAS RECEITAS",
                                                "OUTRAS RECEITAS"));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                accountA,
                                                servicesClassification));

                classificationRepository.save(
                                new AccountingAccountClassification(
                                                accountB,
                                                otherRevenueClassification));

                AccountingAccountClassification classificationA = classificationRepository
                                .findByAccountingAccountId(accountA.getId())
                                .orElseThrow();

                AccountingAccountClassification classificationB = classificationRepository
                                .findByAccountingAccountId(accountB.getId())
                                .orElseThrow();

                assertThat(accountA.getAccountKey())
                                .isEqualTo(accountB.getAccountKey());

                assertThat(classificationA.getClassification().getId())
                                .isEqualTo(servicesClassification.getId());

                assertThat(classificationB.getClassification().getId())
                                .isEqualTo(otherRevenueClassification.getId());
        }
}