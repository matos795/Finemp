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

        @Autowired
        private DreClassificationRepository dreClassificationRepository;

        @Test
        void shouldLinkAccountingAccountToExistingGlobalClassification() {

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

                DreClassification globalClassification = dreClassificationRepository.save(
                                new DreClassification(
                                                "EBITDA",
                                                "RECEITA BRUTA",
                                                "LOCAÇÃO"));

                classificationService.classifyAccount(
                                company.getId(),
                                account.getId(),
                                globalClassification.getId());

                AccountingAccountClassification accountClassification = classificationRepository
                                .findByAccountingAccountId(account.getId())
                                .orElseThrow();

                assertThat(accountClassification.getAccountingAccount().getId())
                                .isEqualTo(account.getId());

                assertThat(accountClassification.getClassification().getId())
                                .isEqualTo(globalClassification.getId());

                assertThat(accountClassification.getClassification()
                                .getDreClassification())
                                .isEqualTo("EBITDA");

                assertThat(accountClassification.getClassification()
                                .getDreOpenClassification())
                                .isEqualTo("RECEITA BRUTA");

                assertThat(accountClassification.getClassification()
                                .getEbitdaClassification())
                                .isEqualTo("LOCAÇÃO");
        }

        @Test
        void shouldReclassifyExistingAccountingAccountWithoutCreatingDuplicate() {

                Company company = companyRepository.save(
                                new Company(
                                                "Empresa Reclassificação Ltda",
                                                "12121212000112"));

                AccountingSource source = accountingSourceRepository.save(
                                new AccountingSource(
                                                company,
                                                AccountingSourceType.QUESTOR,
                                                "company-reclassification",
                                                true));

                AccountingAccount account = accountingAccountRepository.save(
                                new AccountingAccount(
                                                source,
                                                "100",
                                                "3.1.01.100",
                                                "Conta exemplo"));

                DreClassification services = dreClassificationRepository.save(
                                new DreClassification(
                                                "EBITDA",
                                                "RECEITA BRUTA",
                                                "SERVIÇOS"));

                DreClassification rental = dreClassificationRepository.save(
                                new DreClassification(
                                                "EBITDA",
                                                "RECEITA BRUTA",
                                                "LOCAÇÃO"));

                classificationService.classifyAccount(
                                company.getId(),
                                account.getId(),
                                services.getId());

                classificationService.classifyAccount(
                                company.getId(),
                                account.getId(),
                                rental.getId());

                AccountingAccountClassification classification = classificationRepository
                                .findByAccountingAccountId(account.getId())
                                .orElseThrow();

                assertThat(classification.getClassification().getId())
                                .isEqualTo(rental.getId());

                assertThat(classificationRepository.count())
                                .isEqualTo(1);
        }
}