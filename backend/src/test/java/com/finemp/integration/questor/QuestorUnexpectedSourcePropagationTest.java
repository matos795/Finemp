package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.source.AccountingSourceType;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QuestorUnexpectedSourcePropagationTest {
    @Test
    void shouldPreserveUnexpectedSourceDataAcrossVerifiedSnapshotBoundary() throws Exception {
        var provider = mock(QuestorConnectionProvider.class);
        var connection = mock(Connection.class);
        var identities = mock(FirebirdQuestorCompanyIdentityReader.class);
        var movements = mock(FirebirdQuestorAccountingReader.class);
        var request = new AccountingReadRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountingSourceType.QUESTOR,
                "57",
                "52525252000152",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        when(provider.openConnection()).thenReturn(connection);
        when(identities.readOn(connection, request))
                .thenReturn(new ExternalCompanyIdentity("57", "52525252000152"));
        when(movements.readOn(
                connection,
                "57",
                request.startDate(),
                request.endDate()))
                .thenThrow(new QuestorUnexpectedSourceDataException());

        var reader = new VerifiedQuestorSnapshotReader(provider, identities, movements);

        doThrow(new java.sql.SQLException("synthetic private cleanup detail"))
                .when(connection).close();

        assertThatThrownBy(() -> reader.readSnapshot(request))
                .isInstanceOf(QuestorUnexpectedSourceDataException.class)
                .hasMessage("Unexpected accounting source data.")
                .satisfies(error -> {
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getSuppressed()).isEmpty();
                    assertThat(error.toString()).doesNotContain("synthetic private cleanup detail");
                });

        verify(connection).rollback();
        verify(connection).close();
    }
}
