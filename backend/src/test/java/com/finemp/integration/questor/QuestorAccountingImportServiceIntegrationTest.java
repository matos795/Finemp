package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.account.AccountingAccountRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "finemp.integration.questor.enabled=true")
@Transactional
class QuestorAccountingImportServiceIntegrationTest {

    @Autowired
    private QuestorAccountingImportService importService;

    @MockitoBean
    private QuestorAccountingReader questorAccountingReader;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private AccountingMovementRepository accountingMovementRepository;

    @Test
    void shouldImportQuestorMovementsWithoutDuplicatingOnReexecution() {
        Company company = companyRepository.save(
                new Company(
                        "Empresa Exemplo Ltda",
                        "12345678000190"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "external-company-001",
                        true));

        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);

        List<ImportedAccountingMovement> records = List.of(
                new ImportedAccountingMovement(
                        "entry-001",
                        LocalDate.of(2026, 7, 15),
                        "100",
                        "1.1.01.001.100",
                        "Conta de Débito",
                        AccountingMovementSide.DEBIT,
                        new BigDecimal("500.00"),
                        "Histórico de exemplo"),
                new ImportedAccountingMovement(
                        "entry-001",
                        LocalDate.of(2026, 7, 15),
                        "200",
                        "3.1.01.001.200",
                        "Conta de Crédito",
                        AccountingMovementSide.CREDIT,
                        new BigDecimal("500.00"),
                        "Histórico de exemplo"));

        when(questorAccountingReader.readMovements(
                "external-company-001",
                startDate,
                endDate)).thenReturn(records);

        importService.importMovements(
                source.getId(),
                startDate,
                endDate);

        assertThat(accountingAccountRepository.count())
                .isEqualTo(2);

        assertThat(accountingMovementRepository.count())
                .isEqualTo(2);

        importService.importMovements(
                source.getId(),
                startDate,
                endDate);

        assertThat(accountingAccountRepository.count())
                .isEqualTo(2);

        assertThat(accountingMovementRepository.count())
                .isEqualTo(2);
    }
}