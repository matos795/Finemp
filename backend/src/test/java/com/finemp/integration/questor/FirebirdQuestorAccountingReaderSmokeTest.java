package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.util.List;

@EnabledIfEnvironmentVariable(named = "FINEMP_QUESTOR_SMOKE_TEST", matches = "true")
class FirebirdQuestorAccountingReaderSmokeTest {

    @Test
    void shouldReadAccountingMovementsFromRealQuestor() {
        QuestorConnectionProperties properties = new QuestorConnectionProperties(
                requiredEnvironmentVariable("FINEMP_QUESTOR_JDBC_URL"),
                requiredEnvironmentVariable("FINEMP_QUESTOR_USERNAME"),
                requiredEnvironmentVariable("FINEMP_QUESTOR_PASSWORD"));

        QuestorConnectionProvider connectionProvider = new QuestorConnectionProvider(properties);

        QuestorAccountingReader reader = new FirebirdQuestorAccountingReader(connectionProvider);

        String companyReference = requiredEnvironmentVariable("FINEMP_QUESTOR_TEST_COMPANY_REFERENCE");

        LocalDate startDate = LocalDate.parse(
                requiredEnvironmentVariable("FINEMP_QUESTOR_TEST_START_DATE"));

        LocalDate endDate = LocalDate.parse(
                requiredEnvironmentVariable("FINEMP_QUESTOR_TEST_END_DATE"));

        List<ImportedAccountingMovement> movements = reader.readMovements(
                companyReference,
                startDate,
                endDate);

        assertThat(movements).isNotEmpty();

        assertThat(movements)
                .allSatisfy(movement -> {
                    assertThat(movement.sourceEntryKey()).isNotBlank();
                    assertThat(movement.accountingDate()).isBetween(startDate, endDate);
                    assertThat(movement.externalAccountId()).isNotBlank();
                    assertThat(movement.accountKey()).isNotBlank();
                    assertThat(movement.accountDescription()).isNotBlank();
                    assertThat(movement.side()).isNotNull();
                    assertThat(movement.amount()).isNotNull();
                });
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }

        return value;
    }
}