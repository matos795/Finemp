package com.finemp.dre;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.classification.AccountingAccountClassification;
import com.finemp.accounting.classification.AccountingAccountClassificationRepository;
import com.finemp.accounting.movement.AccountingMovement;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.movement.AccountingMovementSide;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DreMovementRepositoryIntegrationTest {

    @Autowired
    private DreMovementRepository dreMovementRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private AccountingAccountClassificationRepository classificationRepository;

    @Autowired
    private AccountingMovementRepository accountingMovementRepository;

    @Test
    void shouldReturnClassifiedAndUnclassifiedMovements() {

        Company company = companyRepository.save(
                new Company(
                        "Empresa Exemplo Ltda",
                        "11111111000111"));

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
                        "3.1.01.100",
                        "Receita classificada"));

        AccountingAccount unclassifiedAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        source,
                        "200",
                        "4.1.01.200",
                        "Despesa pendente"));

        classificationRepository.save(
                new AccountingAccountClassification(
                        classifiedAccount,
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        classifiedAccount,
                        "entry-1",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 7, 10),
                        new BigDecimal("100.00"),
                        "Movimento classificado"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        unclassifiedAccount,
                        "entry-2",
                        AccountingMovementSide.DEBIT,
                        LocalDate.of(2026, 7, 15),
                        new BigDecimal("50.00"),
                        "Movimento sem classificação"));

        List<DreMovementRow> rows = dreMovementRepository.findAllByCompanyAndPeriod(
                company.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        assertThat(rows)
                .hasSize(2);

        DreMovementRow classifiedRow = rows.stream()
                .filter(row -> row.accountId()
                        .equals(classifiedAccount.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(classifiedRow.dreClassification())
                .isEqualTo("EBITDA");

        assertThat(classifiedRow.dreOpenClassification())
                .isEqualTo("RECEITA BRUTA");

        assertThat(classifiedRow.ebitdaClassification())
                .isEqualTo("SERVIÇOS");

        DreMovementRow unclassifiedRow = rows.stream()
                .filter(row -> row.accountId()
                        .equals(unclassifiedAccount.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(unclassifiedRow.dreClassification())
                .isNull();

        assertThat(unclassifiedRow.dreOpenClassification())
                .isNull();

        assertThat(unclassifiedRow.ebitdaClassification())
                .isNull();
    }

    @Test
    void shouldReturnOnlyMovementsFromRequestedCompanyAndPeriod() {

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
                        "3.1.01.100",
                        "Receita empresa A"));

        AccountingAccount accountB = accountingAccountRepository.save(
                new AccountingAccount(
                        sourceB,
                        "200",
                        "3.1.01.200",
                        "Receita empresa B"));

        classificationRepository.save(
                new AccountingAccountClassification(
                        accountA,
                        "RESULTADO",
                        "RECEITA",
                        "SERVIÇOS"));

        classificationRepository.save(
                new AccountingAccountClassification(
                        accountB,
                        "RESULTADO",
                        "RECEITA",
                        "SERVIÇOS"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        accountA,
                        "entry-a-july",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 7, 10),
                        new BigDecimal("100.00"),
                        "Empresa A - julho"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        accountA,
                        "entry-a-august",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 8, 10),
                        new BigDecimal("200.00"),
                        "Empresa A - agosto"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        accountB,
                        "entry-b-july",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 7, 10),
                        new BigDecimal("300.00"),
                        "Empresa B - julho"));

        List<DreMovementRow> rows = dreMovementRepository.findAllByCompanyAndPeriod(
                companyA.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        assertThat(rows)
                .hasSize(1);

        assertThat(rows.getFirst().accountId())
                .isEqualTo(accountA.getId());

        assertThat(rows.getFirst().amount())
                .isEqualByComparingTo("100.00");
    }
}