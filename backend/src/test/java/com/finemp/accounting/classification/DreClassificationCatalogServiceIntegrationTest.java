package com.finemp.accounting.classification;

import com.finemp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DreClassificationCatalogServiceIntegrationTest {

    @Autowired
    private DreClassificationCatalogService catalogService;

    @Autowired
    private DreClassificationRepository repository;

    @Test
    void shouldCreateOnlyMissingGlobalClassifications() {

        repository.save(
                new DreClassification(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"));

        var requested = List.of(
                new DreClassificationKey(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"),

                new DreClassificationKey(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "LOCAÇÃO"));

        int created = catalogService.createMissing(
                requested);

        assertThat(created)
                .isEqualTo(1);

        assertThat(repository.findAll())
                .extracting(
                        DreClassification::getEbitdaClassification)
                .contains(
                        "SERVIÇOS",
                        "LOCAÇÃO");
    }

    @Test
    void shouldBeIdempotentWhenImportRunsAgain() {

        var requested = List.of(
                new DreClassificationKey(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"));

        int first = catalogService.createMissing(
                requested);

        int second = catalogService.createMissing(
                requested);

        assertThat(first)
                .isEqualTo(1);

        assertThat(second)
                .isZero();
    }
}