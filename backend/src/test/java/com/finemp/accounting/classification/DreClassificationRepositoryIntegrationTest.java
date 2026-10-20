package com.finemp.accounting.classification;

import com.finemp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DreClassificationRepositoryIntegrationTest {

    @Autowired
    private DreClassificationRepository repository;

    @Test
    void shouldPersistGlobalDreClassification() {

        DreClassification classification = repository.save(
                new DreClassification(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"));

        DreClassification persisted = repository.findById(classification.getId())
                .orElseThrow();

        assertThat(persisted.getDreClassification())
                .isEqualTo("EBITDA");

        assertThat(persisted.getDreOpenClassification())
                .isEqualTo("RECEITA BRUTA");

        assertThat(persisted.getEbitdaClassification())
                .isEqualTo("SERVIÇOS");
    }

    @Test
    void shouldRejectDuplicateClassificationCombination() {

        repository.saveAndFlush(
                new DreClassification(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                new DreClassification(
                        "EBITDA",
                        "RECEITA BRUTA",
                        "SERVIÇOS")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPreserveSpecialClassificationMarkers() {

        DreClassification classification = repository.save(
                new DreClassification(
                        "0",
                        "(-) Verificar",
                        "N/A"));

        DreClassification persisted = repository.findById(classification.getId())
                .orElseThrow();

        assertThat(persisted.getDreClassification())
                .isEqualTo("0");

        assertThat(persisted.getDreOpenClassification())
                .isEqualTo("(-) Verificar");

        assertThat(persisted.getEbitdaClassification())
                .isEqualTo("N/A");
    }
}