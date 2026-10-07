package com.finemp.accounting.movement;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingMovementQueryRepositoryIntegrationTest {

    @Autowired
    private AccountingMovementRepository accountingMovementRepository;

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldFindMovementsOnlyForCompanyAndPeriod() {
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

        AccountingAccount firstAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        firstSource,
                        "100",
                        "3.1.01.001",
                        "Receita Empresa Um"));

        AccountingAccount secondAccount = accountingAccountRepository.save(
                new AccountingAccount(
                        secondSource,
                        "200",
                        "3.1.01.001",
                        "Receita Empresa Dois"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        firstAccount,
                        "entry-1",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 7, 10),
                        new BigDecimal("1000.00"),
                        "Movimento julho empresa um"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        firstAccount,
                        "entry-2",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 8, 10),
                        new BigDecimal("2000.00"),
                        "Movimento agosto empresa um"));

        accountingMovementRepository.save(
                new AccountingMovement(
                        secondAccount,
                        "entry-3",
                        AccountingMovementSide.CREDIT,
                        LocalDate.of(2026, 7, 15),
                        new BigDecimal("3000.00"),
                        "Movimento julho empresa dois"));

        entityManager.flush();
        entityManager.clear();

        List<AccountingMovement> movements = accountingMovementRepository.findAllByCompanyAndPeriod(
                firstCompany.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        assertThat(movements)
                .hasSize(1);

        AccountingMovement movement = movements.getFirst();

        assertThat(movement.getSourceEntryKey())
                .isEqualTo("entry-1");

        assertThat(movement.getAccountingDate())
                .isEqualTo(LocalDate.of(2026, 7, 10));

        assertThat(movement.getAccount().getDescription())
                .isEqualTo("Receita Empresa Um");
    }
}