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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingMovementRepositoryIntegrationTest {

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
    void shouldPersistAccountingMovement() {
        Company company = companyRepository.save(
                new Company("Empresa Exemplo Ltda", "12345678000190"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "company-ref",
                        true));

        AccountingAccount account = accountingAccountRepository.save(
                new AccountingAccount(
                        source,
                        "5396",
                        "3.1.01.005.001.009.5396",
                        "Receita de Exemplo"));

        AccountingMovement movement = new AccountingMovement(
                account,
                "entry-123",
                AccountingMovementSide.CREDIT,
                LocalDate.of(2026, 7, 15),
                new BigDecimal("1250.75"),
                "Histórico de exemplo");

        AccountingMovement saved = accountingMovementRepository.save(movement);

        entityManager.flush();
        entityManager.clear();

        AccountingMovement retrieved = accountingMovementRepository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getAccount().getId()).isEqualTo(account.getId());
        assertThat(retrieved.getSourceEntryKey()).isEqualTo("entry-123");
        assertThat(retrieved.getSide()).isEqualTo(AccountingMovementSide.CREDIT);
        assertThat(retrieved.getAccountingDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(retrieved.getAmount()).isEqualByComparingTo("1250.75");
        assertThat(retrieved.getHistory()).isEqualTo("Histórico de exemplo");
    }
}