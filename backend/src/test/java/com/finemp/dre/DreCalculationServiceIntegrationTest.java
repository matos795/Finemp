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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DreCalculationServiceIntegrationTest {

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

    private DreCalculationService service;

    @BeforeEach
    void setUp() {
        service = new DreCalculationService(
                dreMovementRepository,
                new DreMovementValueCalculator());
    }

    @Test
    void shouldCalculateDreAndReportUnclassifiedAccounts() {

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

        AccountingAccount revenueAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        source,
                        "100",
                        "3.1.01.100",
                        "Receita de serviços"));

        AccountingAccount expenseAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        source,
                        "200",
                        "4.1.01.200",
                        "Despesa operacional"));

        AccountingAccount unclassifiedAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        source,
                        "300",
                        "4.1.01.300",
                        "Conta pendente"));

        classificationRepository.save(
                new AccountingAccountClassification(
                        revenueAccount,
                        "RESULTADO",
                        "RECEITA",
                        "SERVIÇOS"));

        classificationRepository.save(
                new AccountingAccountClassification(
                        expenseAccount,
                        "RESULTADO",
                        "DESPESAS",
                        "OPERACIONAIS"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        revenueAccount,
                        "entry-1",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 7, 10),
                        new BigDecimal("100.00"),
                        "Receita"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        expenseAccount,
                        "entry-2",
                        AccountingMovementSide.DEBIT,
                        LocalDate.of(2026, 7, 15),
                        new BigDecimal("40.00"),
                        "Despesa"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        unclassifiedAccount,
                        "entry-3",
                        AccountingMovementSide.DEBIT,
                        LocalDate.of(2026, 7, 20),
                        new BigDecimal("25.00"),
                        "Movimento pendente"));

        DreCalculationResult result = service.calculate(
                company.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        assertThat(result.lines())
                .hasSize(2);

        DreLine revenueLine = result.lines().stream()
                .filter(line -> line.dreOpenClassification()
                        .equals("RECEITA"))
                .findFirst()
                .orElseThrow();

        assertThat(revenueLine.dreClassification())
                .isEqualTo("RESULTADO");

        assertThat(revenueLine.ebitdaClassification())
                .isEqualTo("SERVIÇOS");

        assertThat(revenueLine.total())
                .isEqualByComparingTo("100.00");

        DreLine expenseLine = result.lines().stream()
                .filter(line -> line.dreOpenClassification()
                        .equals("DESPESAS"))
                .findFirst()
                .orElseThrow();

        assertThat(expenseLine.dreClassification())
                .isEqualTo("RESULTADO");

        assertThat(expenseLine.ebitdaClassification())
                .isEqualTo("OPERACIONAIS");

        assertThat(expenseLine.total())
                .isEqualByComparingTo("-40.00");

        assertThat(result.unclassifiedAccountCount())
                .isEqualTo(1);
    }
}