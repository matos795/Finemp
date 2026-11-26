package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountingFileImportExceptionHandlerTest {

    private final AccountingFileImportExceptionHandler handler =
            new AccountingFileImportExceptionHandler();

    @Test
    void shouldMapUnavailableSourceToSanitizedConflict() {
        var response = handler.conflict(new AccountingFileImportConflictException(
                AccountingFileImportConflictException.Reason.SOURCE_NOT_AVAILABLE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SOURCE_NOT_AVAILABLE");
        assertThat(response.getBody().message())
                .isEqualTo("A importação conflita com o estado atual da fonte contábil.");
    }

    @Test
    void shouldMapIdempotencyMismatchWithoutLeakingState() {
        var response = handler.conflict(new AccountingFileImportConflictException(
                AccountingFileImportConflictException.Reason.IDEMPOTENCY_MISMATCH));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
}
