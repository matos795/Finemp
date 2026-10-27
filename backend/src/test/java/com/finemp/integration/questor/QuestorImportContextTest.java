package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.accounting.ingestion.AccountingMovementIngestionService;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.reading.AccountingSourceReaderRegistry;
import com.finemp.accounting.source.*;
import com.finemp.company.Company;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuestorImportContextTest {
    private final AccountingSourceRepository sources = mock(AccountingSourceRepository.class);
    private final AccountingSourceReaderRegistry readers = mock(AccountingSourceReaderRegistry.class);
    private final AccountingMovementIngestionService ingestion = mock(AccountingMovementIngestionService.class);
    private final AccountingSource source = mock(AccountingSource.class);
    private final Company company = mock(Company.class);
    private final UUID sourceId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final LocalDate start = LocalDate.of(2026, 7, 1);
    private final LocalDate end = LocalDate.of(2026, 7, 31);
    private final QuestorAccountingImportService service = new QuestorAccountingImportService(sources, readers, ingestion);

    @BeforeEach
    void prepare() {
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.of(source));
        when(source.getId()).thenReturn(sourceId);
        when(source.isActive()).thenReturn(true);
        when(source.getType()).thenReturn(AccountingSourceType.QUESTOR);
        when(source.getConnectionMode()).thenReturn(AccountingConnectionMode.DIRECT);
        when(source.getExternalReference()).thenReturn("synthetic-ref");
        when(source.getCompany()).thenReturn(company);
        when(company.getId()).thenReturn(companyId);
        when(company.getCnpj()).thenReturn("19191919000119");
    }

    @Test
    void shouldCarryCompanyExpectationToCanonicalPortBeforeAtomicIngestion() {
        when(readers.readMovements(any())).thenReturn(List.of());
        service.importMovements(sourceId, start, end);
        var request = ArgumentCaptor.forClass(AccountingReadRequest.class);
        verify(readers).readMovements(request.capture());
        assertThat(request.getValue().companyId()).isEqualTo(companyId);
        assertThat(request.getValue().accountingSourceId()).isEqualTo(sourceId);
        assertThat(request.getValue().expectedCompanyCnpj()).isEqualTo("19191919000119");
        verify(ingestion).ingest(sourceId, List.of());
    }

    @Test
    void shouldNeverReadOrIngestInactiveSource() {
        when(source.isActive()).thenReturn(false);
        assertThatThrownBy(() -> service.importMovements(sourceId, start, end)).isInstanceOf(QuestorSourceStateException.class);
        verifyNoInteractions(readers, ingestion);
    }
    @Test void shouldNeverUseTheStudyImportForAConnectorBoundSource() {
        when(source.getConnectionMode()).thenReturn(AccountingConnectionMode.CONNECTOR);
        assertThatThrownBy(() -> service.importMovements(sourceId, start, end)).isInstanceOf(QuestorSourceStateException.class);
        verifyNoInteractions(readers, ingestion);
    }

    @Test
    void shouldNeverReadOrIngestAnInvalidPeriod() {
        assertThatThrownBy(() -> service.importMovements(sourceId, end, start)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(readers, ingestion);
    }

    @Test
    void shouldPreservePreviouslyImportedDataByNeverInvokingIngestionWhenReadFails() {
        when(readers.readMovements(any())).thenThrow(new QuestorReadException("Synthetic failure", new IllegalStateException("Synthetic private cause")));
        assertThatThrownBy(() -> service.importMovements(sourceId, start, end)).isInstanceOf(QuestorReadException.class);
        verifyNoInteractions(ingestion);
        var problem = new QuestorImportExceptionHandler().readFailed();
        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getDetail()).doesNotContain("Synthetic private cause", "SQL", "jdbc:");
    }
}
