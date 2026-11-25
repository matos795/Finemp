package com.finemp.accounting.canonical.v2;

import static org.assertj.core.api.Assertions.*;

import com.finemp.accounting.movement.AccountingMovementSide;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalV2ToLegacyMovementsConverterTest {
    private final CanonicalAccountingAccount debit = account("100", "1.1", "Debit account");
    private final CanonicalAccountingAccount credit = account("200", "2.1", "Credit account");

    @Test
    void shouldExpandAOneSidedEntryIntoOneLegacyMovement() {
        var entry = new CanonicalAccountingEntry(
                "e1",
                LocalDate.of(2026, 7, 5),
                "100",
                null,
                new BigDecimal("10.2500"),
                "1",
                "Synthetic");

        var movements = CanonicalV2ToLegacyMovementsConverter.convert(
                List.of(entry),
                Map.of("100", debit));

        assertThat(movements).singleElement().satisfies(row -> {
            assertThat(row.sourceEntryKey()).isEqualTo("e1");
            assertThat(row.externalAccountId()).isEqualTo("100");
            assertThat(row.accountKey()).isEqualTo("1.1.100");
            assertThat(row.accountDescription()).isEqualTo("Debit account");
            assertThat(row.side()).isEqualTo(AccountingMovementSide.DEBIT);
            assertThat(row.amount()).isEqualByComparingTo("10.2500");
            assertThat(row.history()).isEqualTo("Synthetic");
        });
    }

    @Test
    void shouldExpandATwoSidedEntryWithoutChangingTheAmount() {
        var entry = new CanonicalAccountingEntry(
                "e2",
                LocalDate.of(2026, 7, 6),
                "100",
                "200",
                new BigDecimal("-12.3400"),
                "1",
                null);

        var movements = CanonicalV2ToLegacyMovementsConverter.convert(
                List.of(entry),
                Map.of("100", debit, "200", credit));

        assertThat(movements)
                .hasSize(2)
                .extracting(row -> row.side())
                .containsExactly(AccountingMovementSide.DEBIT, AccountingMovementSide.CREDIT);
        assertThat(movements)
                .allSatisfy(row -> assertThat(row.amount()).isEqualByComparingTo("-12.3400"));
    }

    @Test
    void shouldDeduplicateScaleOnlyDifferencesInCanonicalAmounts() {
        var first = new CanonicalAccountingEntry(
                "e-scale",
                LocalDate.of(2026, 7, 6),
                "100",
                null,
                new BigDecimal("10.0"),
                null,
                null);
        var sameValue = new CanonicalAccountingEntry(
                "e-scale",
                LocalDate.of(2026, 7, 6),
                "100",
                null,
                new BigDecimal("10.00"),
                null,
                null);

        var movements = CanonicalV2ToLegacyMovementsConverter.convert(
                List.of(first, sameValue),
                Map.of("100", debit));

        assertThat(movements).singleElement()
                .satisfies(row -> assertThat(row.amount()).isEqualByComparingTo("10"));
    }

    @Test
    void shouldRejectMissingAccountDescriptorsInsteadOfInventingLegacyData() {
        var entry = new CanonicalAccountingEntry(
                "e3",
                LocalDate.of(2026, 7, 6),
                "999",
                null,
                BigDecimal.ONE,
                null,
                null);

        assertThatThrownBy(() -> CanonicalV2ToLegacyMovementsConverter.convert(List.of(entry), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Canonical account descriptor is missing.");
    }

    private CanonicalAccountingAccount account(String id, String classification, String description) {
        return new CanonicalAccountingAccount(
                id,
                classification,
                description,
                new SourceAccountCodes("2", "1", "1", "1", "2", null, null));
    }
}
