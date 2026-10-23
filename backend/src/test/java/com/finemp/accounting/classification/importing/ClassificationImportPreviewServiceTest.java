package com.finemp.accounting.classification.importing;

import com.finemp.accounting.classification.AccountingAccountClassificationQueryService;
import com.finemp.accounting.classification.DreClassification;
import com.finemp.accounting.classification.DreClassificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ClassificationImportPreviewServiceTest {

    @Test
    void shouldBuildPreviewUsingCompanyAccountsAndPlanoQMappings() {

        var mappingReader = Mockito.mock(PlanoQMappingReader.class);

        var accountQueryService = Mockito.mock(
                AccountingAccountClassificationQueryService.class);

        var dreClassificationRepository = Mockito.mock(
                DreClassificationRepository.class);

        var calculator = new ClassificationImportPreviewCalculator();

        var service = new ClassificationImportPreviewService(
                mappingReader,
                accountQueryService,
                dreClassificationRepository,
                calculator);

        UUID companyId = UUID.randomUUID();

        UUID accountId = UUID.randomUUID();

        var inputStream = new ByteArrayInputStream(
                new byte[0]);

        var account = new com.finemp.accounting.classification.AccountingAccountClassificationResponse(
                accountId,
                UUID.randomUUID(),
                "3.1.01.100",
                "Receita de Serviços",
                "PENDING",
                null,
                null,
                null,
                null);

        var mapping = new PlanoQMapping(
                "3.1.01.100",
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        DreClassification classification = Mockito.mock(
                DreClassification.class);

        UUID classificationId = UUID.randomUUID();

        when(classification.getId())
                .thenReturn(classificationId);

        when(classification.getDreClassification())
                .thenReturn("EBITDA");

        when(classification.getDreOpenClassification())
                .thenReturn("RECEITA BRUTA");

        when(classification.getEbitdaClassification())
                .thenReturn("SERVIÇOS");

        when(mappingReader.read(inputStream))
                .thenReturn(List.of(mapping));

        when(accountQueryService.findAllByCompanyId(companyId))
                .thenReturn(List.of(account));

        when(dreClassificationRepository.findAll())
                .thenReturn(List.of(classification));

        var result = service.preview(
                companyId,
                inputStream);

        assertThat(result)
                .hasSize(1);

        assertThat(result.getFirst().status())
                .isEqualTo(
                        ClassificationImportStatus.READY);

        assertThat(result.getFirst().accountId())
                .isEqualTo(accountId);

        assertThat(result.getFirst().proposedClassificationId())
                .isEqualTo(classificationId);
    }
}