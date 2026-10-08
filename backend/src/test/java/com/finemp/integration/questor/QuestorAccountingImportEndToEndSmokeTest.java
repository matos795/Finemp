package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.movement.AccountingMovement;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "finemp.integration.questor.enabled=true")
@EnabledIfEnvironmentVariable(named = "FINEMP_QUESTOR_SMOKE_TEST", matches = "true")
class QuestorAccountingImportEndToEndSmokeTest {

    @Autowired
    private QuestorAccountingImportService importService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private AccountingAccountRepository accountingAccountRepository;

    @Autowired
    private AccountingMovementRepository accountingMovementRepository;

    @Test
    void shouldImportRealQuestorMovementsIntoFinempPostgres() {

        String externalCompanyReference = requiredEnvironmentVariable(
                "FINEMP_QUESTOR_TEST_COMPANY_REFERENCE");

        LocalDate startDate = LocalDate.parse(
                requiredEnvironmentVariable(
                        "FINEMP_QUESTOR_TEST_START_DATE"));

        LocalDate endDate = LocalDate.parse(
                requiredEnvironmentVariable(
                        "FINEMP_QUESTOR_TEST_END_DATE"));

        Company company = companyRepository.save(
                new Company(
                        "Smoke Test Company",
                        "00000000000000"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        externalCompanyReference,
                        true));

        importService.importMovements(
                source.getId(),
                startDate,
                endDate);

        long accountsAfterFirstImport = accountingAccountRepository.count();

        long movementsAfterFirstImport = accountingMovementRepository.count();

        assertThat(accountsAfterFirstImport)
                .isGreaterThan(0);

        assertThat(movementsAfterFirstImport)
                .isGreaterThan(0);

        List<AccountingMovement> movements = accountingMovementRepository
                .findAllByCompanyAndPeriod(
                        company.getId(),
                        startDate,
                        endDate);

        assertThat(movements)
                .isNotEmpty();

        importService.importMovements(
                source.getId(),
                startDate,
                endDate);

        assertThat(accountingAccountRepository.count())
                .isEqualTo(accountsAfterFirstImport);

        assertThat(accountingMovementRepository.count())
                .isEqualTo(movementsAfterFirstImport);
    }

    private String requiredEnvironmentVariable(
            String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is missing: "
                            + name);
        }

        return value;
    }
}