package com.finemp.accounting.classification.importing;

import org.junit.jupiter.api.Test;

import com.finemp.accounting.classification.AccountingAccountClassificationResponse;
import com.finemp.accounting.classification.DreClassificationResponse;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationImportPreviewCalculatorTest {

    private final ClassificationImportPreviewCalculator calculator = new ClassificationImportPreviewCalculator();

    @Test
    void shouldMarkPendingAccountAsReadyWhenMappingAndClassificationExist() {

        UUID accountId = UUID.randomUUID();
        UUID classificationId = UUID.randomUUID();

        var account = new AccountingAccountClassificationResponse(
                accountId,
                UUID.randomUUID(),
                "3.1.01.005.001.009.5396",
                "Receita de Locação de Imóveis",
                "PENDING",
                null,
                null,
                null,
                null);

        var classification = new DreClassificationResponse(
                classificationId,
                "EBITDA",
                "RECEITA BRUTA",
                "LOCAÇÃO DE BENS MÓVEIS");

        var mapping = new PlanoQMapping(
                "3.1.01.005.001.009.5396",
                "EBITDA",
                "RECEITA BRUTA",
                "LOCAÇÃO DE BENS MÓVEIS");

        var result = calculator.calculate(
                List.of(account),
                List.of(classification),
                List.of(mapping));

        assertThat(result)
                .hasSize(1);

        assertThat(result.getFirst().status())
                .isEqualTo(
                        ClassificationImportStatus.READY);

        assertThat(result.getFirst().proposedClassificationId())
                .isEqualTo(classificationId);
    }

    @Test
    void shouldMarkAccountAsMappingNotFoundWhenPlanoQDoesNotContainAccount() {

        var account = new AccountingAccountClassificationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "4.1.01.001",
                "Despesa Administrativa",
                "PENDING",
                null,
                null,
                null,
                null);

        var calculator = new ClassificationImportPreviewCalculator();

        var result = calculator.calculate(
                List.of(account),
                List.of(),
                List.of());

        assertThat(result.getFirst().status())
                .isEqualTo(
                        ClassificationImportStatus.MAPPING_NOT_FOUND);

        assertThat(result.getFirst().proposedClassificationId())
                .isNull();
    }

    @Test
    void shouldMarkAccountAsClassificationNotFoundWhenCatalogDoesNotContainMapping() {

        var account = new AccountingAccountClassificationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "4.1.01.002",
                "Despesa Tributária",
                "PENDING",
                null,
                null,
                null,
                null);

        var mapping = new PlanoQMapping(
                "4.1.01.002",
                "EBITDA",
                "(-) DESPESAS",
                "(-) TRIBUTÁRIAS");

        var calculator = new ClassificationImportPreviewCalculator();

        var result = calculator.calculate(
                List.of(account),
                List.of(),
                List.of(mapping));

        assertThat(result.getFirst().status())
                .isEqualTo(
                        ClassificationImportStatus.CLASSIFICATION_NOT_FOUND);

        assertThat(result.getFirst().dreClassification())
                .isEqualTo("EBITDA");

        assertThat(result.getFirst().dreOpenClassification())
                .isEqualTo("(-) DESPESAS");

        assertThat(result.getFirst().ebitdaClassification())
                .isEqualTo("(-) TRIBUTÁRIAS");
    }

    @Test
    void shouldMarkAccountAsAlreadyClassifiedWhenCurrentClassificationMatchesMapping() {

        UUID classificationId = UUID.randomUUID();

        var account = new AccountingAccountClassificationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "3.1.01.100",
                "Receita de Serviços",
                "CLASSIFIED",
                classificationId,
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        var classification = new DreClassificationResponse(
                classificationId,
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        var mapping = new PlanoQMapping(
                "3.1.01.100",
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        var calculator = new ClassificationImportPreviewCalculator();

        var result = calculator.calculate(
                List.of(account),
                List.of(classification),
                List.of(mapping));

        assertThat(result.getFirst().status())
                .isEqualTo(
                        ClassificationImportStatus.ALREADY_CLASSIFIED);
    }

    @Test
    void shouldMarkAccountAsConflictWhenCurrentClassificationDiffersFromMapping() {

        UUID currentClassificationId = UUID.randomUUID();

        UUID proposedClassificationId = UUID.randomUUID();

        var account = new AccountingAccountClassificationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "3.1.01.200",
                "Receita Exemplo",
                "CLASSIFIED",
                currentClassificationId,
                "FINANCEIRAS",
                "RECEITA FINANCEIRA",
                "JUROS");

        var proposedClassification = new DreClassificationResponse(
                proposedClassificationId,
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        var mapping = new PlanoQMapping(
                "3.1.01.200",
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        var calculator = new ClassificationImportPreviewCalculator();

        var result = calculator.calculate(
                List.of(account),
                List.of(proposedClassification),
                List.of(mapping));

        assertThat(result.getFirst().status())
                .isEqualTo(
                        ClassificationImportStatus.CONFLICT);

        assertThat(result.getFirst().currentClassificationId())
                .isEqualTo(currentClassificationId);

        assertThat(result.getFirst().proposedClassificationId())
                .isEqualTo(proposedClassificationId);
    }
}