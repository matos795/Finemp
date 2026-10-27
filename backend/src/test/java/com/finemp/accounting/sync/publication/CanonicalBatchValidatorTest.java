package com.finemp.accounting.sync.publication;

import static org.assertj.core.api.Assertions.*;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class CanonicalBatchValidatorTest {
    private final LocalDate start = LocalDate.of(2026,7,1), end = LocalDate.of(2026,7,31);
    private final AccountingReadRequest request = new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(),
            AccountingSourceType.QUESTOR, "synthetic", "30303030000130", start, end);
    @Test void shouldPreserveSignedAmountsAndLiteralFieldsWithoutRounding() {
        var batch = validate(List.of(row("entry", "1.23000", start, "0", "N/A")), 10);
        assertThat(batch.uniqueMovements().getFirst().amount()).isEqualByComparingTo("1.2300").hasScaleOf(4);
        assertThat(batch.uniqueMovements().getFirst().accountKey()).isEqualTo("0");
        assertThat(batch.uniqueMovements().getFirst().accountDescription()).isEqualTo("N/A");
        assertThat(validate(List.of(row("negative", "-1.2345", end, "key", "Synthetic")), 10)
                .uniqueMovements().getFirst().amount()).isEqualByComparingTo("-1.2345");
    }
    @Test void shouldCountAndSkipOnlyIdenticalRepeatedIdentitiesInsideTheBatch() {
        var first = row("entry", "1.0", start, "key", "Synthetic");
        var sameValue = row("entry", "1.0000", start, "key", "Synthetic");
        var batch = validate(List.of(first, sameValue), 10);
        assertThat(batch.readCount()).isEqualTo(2);
        assertThat(batch.uniqueMovements()).hasSize(1);
        assertThatThrownBy(() -> batch.uniqueMovements().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void shouldRejectChangedIdentityAndContradictoryAccountMetadataWithoutChoosingAReconciliationRule() {
        assertRejected(() -> validate(List.of(row("entry", "1", start, "key", "Synthetic"),
                row("entry", "2", start, "key", "Synthetic")), 10), SyncFailureCode.DATA_CONFLICT);
        assertRejected(() -> validate(List.of(row("entry1", "1", start, "key", "Synthetic"),
                row("entry2", "1", start, "other-key", "Synthetic")), 10), SyncFailureCode.DATA_CONFLICT);
    }
    @Test void shouldRejectUnrepresentableMoneyRatherThanSilentlyRoundOrTruncate() {
        assertRejected(() -> validate(List.of(row("entry", "1.23456", start, "key", "Synthetic")), 10), SyncFailureCode.INVALID_PAYLOAD);
        assertRejected(() -> validate(List.of(row("entry", "1000000000000000", start, "key", "Synthetic")), 10), SyncFailureCode.INVALID_PAYLOAD);
    }
    @Test void shouldRejectOutOfWindowMissingOrOversizedFieldsAndResourceOverflow() {
        assertRejected(() -> validate(List.of(row("entry", "1", start.minusDays(1), "key", "Synthetic")), 10), SyncFailureCode.INVALID_PAYLOAD);
        assertRejected(() -> validate(List.of(row("entry", "1", start, "x".repeat(256), "Synthetic")), 10), SyncFailureCode.INVALID_PAYLOAD);
        assertRejected(() -> validate(Arrays.asList((ImportedAccountingMovement) null), 10), SyncFailureCode.INVALID_PAYLOAD);
        assertRejected(() -> validate(List.of(row("entry1", "1", start, "key", "Synthetic"),
                row("entry2", "1", start, "key", "Synthetic")), 1), SyncFailureCode.RESOURCE_LIMIT);
    }
    @Test void shouldRepresentAValidEmptyReadWithoutInventingMovements() {
        var batch = validate(List.of(), 10);
        assertThat(batch.readCount()).isZero(); assertThat(batch.uniqueMovements()).isEmpty();
        assertThat(batch.toString()).doesNotContain("Synthetic", "30303030000130");
    }
    @Test void shouldBoundExponentWorkBeforeRescalingAndPreserveExactZeroAndNumericBoundary() {
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2), () -> {
            for (var exponent : List.of("1e2147483640", "1e-2147483640", "-1e2147483640"))
                assertRejected(() -> validate(List.of(row("entry", exponent, start, "key", "Synthetic")), 10), SyncFailureCode.INVALID_PAYLOAD);
            assertThat(validate(List.of(row("zero", "0e-2147483647", start, "key", "Synthetic")), 10)
                    .uniqueMovements().getFirst().amount()).isEqualByComparingTo("0.0000").hasScaleOf(4);
        });
        assertThat(validate(List.of(row("limit", "999999999999999.9999", start, "key", "Synthetic")), 10)
                .uniqueMovements().getFirst().amount()).isEqualByComparingTo("999999999999999.9999");
        assertThat(validate(List.of(row("exact", "1.00000000000e2", start, "key", "Synthetic")), 10)
                .uniqueMovements().getFirst().amount()).isEqualByComparingTo("100.0000");
    }
    private ValidatedAccountingBatch validate(List<ImportedAccountingMovement> movements, int max) {
        return CanonicalBatchValidator.validate(request, movements, max);
    }
    private ImportedAccountingMovement row(String key, String amount, LocalDate date, String accountKey, String description) {
        return new ImportedAccountingMovement(key, date, "synthetic-account", accountKey, description, AccountingMovementSide.DEBIT,
                new BigDecimal(amount), "Synthetic history");
    }
    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, SyncFailureCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SyncPublicationRejectedException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }
}

