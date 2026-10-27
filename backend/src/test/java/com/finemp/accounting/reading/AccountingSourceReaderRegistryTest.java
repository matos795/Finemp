package com.finemp.accounting.reading;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.integration.questor.QuestorAccountingReader;
import com.finemp.integration.questor.QuestorSourceReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountingSourceReaderRegistryTest {
    @Test
    void shouldBridgeCanonicalContextWithoutChangingTheProvenQuestorArgumentsOrAmounts() {
        var delegate = mock(QuestorAccountingReader.class);
        var request = request("synthetic-ref", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        var records = new ArrayList<>(List.of(new ImportedAccountingMovement("synthetic-entry", LocalDate.of(2026, 7, 10),
                "synthetic-account", "1.1.synthetic", "Synthetic", AccountingMovementSide.DEBIT,
                new BigDecimal("123.4500"), "Synthetic")));
        when(delegate.readMovements(request.externalCompanyReference(), request.startDate(), request.endDate())).thenReturn(records);
        var registry = new AccountingSourceReaderRegistry(List.of(new QuestorSourceReader(delegate)));
        var actual = registry.readMovements(request);
        records.clear();
        assertThat(actual).hasSize(1);
        assertThat(actual.getFirst().amount()).isEqualByComparingTo("123.4500");
        assertThat(actual.getFirst().side()).isEqualTo(AccountingMovementSide.DEBIT);
        assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        verify(delegate).readMovements("synthetic-ref", request.startDate(), request.endDate());
    }

    @Test
    void shouldFailFastForDuplicateAdaptersAndFailClosedWhenAdapterIsDisabled() {
        var adapter = new QuestorSourceReader(mock(QuestorAccountingReader.class));
        assertThatThrownBy(() -> new AccountingSourceReaderRegistry(List.of(adapter, adapter))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AccountingSourceReaderRegistry(List.of()).readMovements(
                request("synthetic-ref", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))))
                .isInstanceOf(AccountingReaderUnavailableException.class);
    }

    @Test
    void shouldRejectInvalidContextBeforeExternalReadAndRedactReferenceAndCnpjFromToString() {
        assertThatThrownBy(() -> request(" ", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("synthetic-ref", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("synthetic-ref", null, LocalDate.of(2026, 7, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request("synthetic-ref", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).toString())
                .doesNotContain("synthetic-ref", "19191919000119");
    }

    private AccountingReadRequest request(String reference, LocalDate start, LocalDate end) {
        return new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
                reference, "19191919000119", start, end);
    }
}
