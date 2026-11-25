package com.finemp.accounting.canonical.v2;

import static org.assertj.core.api.Assertions.*;

import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalV2BatchValidatorTest {
    private final LocalDate start = LocalDate.of(2026, 7, 1);
    private final LocalDate end = LocalDate.of(2026, 7, 31);

    @Test
    void shouldRejectEntryWithoutAnySide() {
        var batch = batch(
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                entries(new CanonicalAccountingEntry("e1", start, null, null, new BigDecimal("10.00"), null, null)));

        assertInvalid(batch);
    }

    @Test
    void shouldRejectEntryWithTheSameDebitAndCreditAccount() {
        var batch = batch(
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                entries(new CanonicalAccountingEntry("e1", start, "100", "100", new BigDecimal("10.00"), null, null)));

        assertInvalid(batch);
    }

    @Test
    void shouldRejectUnrepresentableDecimalWithoutRounding() {
        var batch = batch(
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                entries(new CanonicalAccountingEntry("e1", start, "100", null, new BigDecimal("1.00001"), null, null)));

        assertInvalid(batch);
    }

    @Test
    void shouldApplyTheDeclaredAmountSignPolicyWithoutInventingAQuestorRule() {
        var negative = new CanonicalAccountingEntry("e1", start, "100", null, new BigDecimal("-10.00"), null, null);

        assertInvalid(batch(
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.NON_NEGATIVE_WITH_SIDE),
                null,
                entries(negative)));

        assertThatCode(() -> CanonicalV2BatchValidator.validate(
                batch(
                        capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                        null,
                        entries(negative)),
                start,
                end,
                100))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRequireReferencedAccountsFromTheChartWhenChartCapabilityIsDeclared() {
        var chart = new CanonicalChartSnapshot(
                Instant.parse("2026-07-31T12:00:00Z"),
                List.of(account("100", "1.1", "Cash")));

        var batch = batch(
                capabilities(Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                chart,
                entries(new CanonicalAccountingEntry("e1", start, "999", null, BigDecimal.ONE, null, null)));

        assertInvalid(batch);
    }

    @Test
    void shouldRequireObservedAccountDescriptorWhenMovementsAreDeclaredWithoutChart() {
        var entry = new CanonicalAccountingEntry(
                "e-observed-missing", start, "100", null, BigDecimal.ONE, null, null);
        var batch = new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                null,
                entries(entry),
                null,
                null,
                null,
                null,
                null);

        assertInvalid(batch);
    }

    @Test
    void shouldAcceptObservedAccountDescriptorsWhenChartIsNotDeclared() {
        var entry = new CanonicalAccountingEntry(
                "e-observed-ok", start, "100", null, BigDecimal.ONE, null, null);
        var batch = new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                List.of(new CanonicalObservedAccountDescriptor("100", "1.1", "Observed")),
                entries(entry),
                null,
                null,
                null,
                null,
                null);

        assertThatCode(() -> CanonicalV2BatchValidator.validate(batch, start, end, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDivergentObservedAccountDescriptorsAsDataConflict() {
        var entry = new CanonicalAccountingEntry(
                "e-observed-conflict", start, "100", null, BigDecimal.ONE, null, null);
        var batch = new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                List.of(
                        new CanonicalObservedAccountDescriptor("100", "1.1", "Observed"),
                        new CanonicalObservedAccountDescriptor("100", "1.1", "Changed")),
                entries(entry),
                null,
                null,
                null,
                null,
                null);

        assertConflict(batch);
    }

    @Test
    void shouldUseChartAsAuthorityWithoutRequiringObservedDescriptors() {
        var entry = new CanonicalAccountingEntry(
                "e-chart-authority", start, "100", null, BigDecimal.ONE, null, null);
        var batch = new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                capabilities(
                        Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS),
                        AmountSignPolicy.SIGNED_WITH_SIDE),
                chart(account("100", "1.1", "Chart account")),
                null,
                entries(entry),
                null,
                null,
                null,
                null,
                null);

        assertThatCode(() -> CanonicalV2BatchValidator.validate(batch, start, end, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTolerateIdenticalDuplicateEntriesButRejectDivergentContent() {
        var first = new CanonicalAccountingEntry("e1", start, "100", null, new BigDecimal("10.00"), null, "same");
        var identical = new CanonicalAccountingEntry("e1", start, "100", null, new BigDecimal("10.0"), null, "same");
        var divergent = new CanonicalAccountingEntry("e1", start, "100", null, new BigDecimal("11.00"), null, "same");
        var caps = capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE);

        assertThatCode(() -> CanonicalV2BatchValidator.validate(
                batch(caps, null, entries(first, identical)),
                start,
                end,
                100))
                .doesNotThrowAnyException();

        assertConflict(batch(caps, null, entries(first, divergent)));
    }

    @Test
    void shouldTolerateIdenticalDuplicateAccountsButRejectDivergentDescriptions() {
        var first = account("100", "1.1", "Cash");
        var identical = account("100", "1.1", "Cash");
        var divergent = account("100", "1.1", "Changed");
        var caps = capabilities(Set.of(AccountingDataset.CHART), AmountSignPolicy.SIGNED_WITH_SIDE);

        assertThatCode(() -> CanonicalV2BatchValidator.validate(
                batch(caps, chart(first, identical), null),
                start,
                end,
                100))
                .doesNotThrowAnyException();

        assertConflict(batch(caps, chart(first, divergent), null));
    }

    @Test
    void shouldAllowOptionalDatasetsToRemainAbsent() {
        var batch = batch(
                capabilities(Set.of(AccountingDataset.MOVEMENTS), AmountSignPolicy.SIGNED_WITH_SIDE),
                null,
                new CanonicalEntriesDataset(start, end, List.of(), null));

        assertThatCode(() -> CanonicalV2BatchValidator.validate(batch, start, end, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldCompareDuplicateMoneyNumericallyAcrossCanonicalDatasets() {
        var entry = new CanonicalAccountingEntry(
                "e1", start, "100", null, new BigDecimal("10.0"), null, null);
        var caps = capabilities(
                Set.of(
                        AccountingDataset.MOVEMENTS,
                        AccountingDataset.DAILY_TOTALS,
                        AccountingDataset.MONTHLY_TOTALS,
                        AccountingDataset.COST_ALLOCATIONS),
                AmountSignPolicy.SIGNED_WITH_SIDE);
        var batch = new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                caps,
                null,
                List.of(new CanonicalObservedAccountDescriptor("100", "1", "Observed 100")),
                entries(entry),
                List.of(
                        new CanonicalAccountDailyDebitCreditTotals("1", "100", start, "LN", new BigDecimal("10.0"), BigDecimal.ZERO),
                        new CanonicalAccountDailyDebitCreditTotals("1", "100", start, "LN", new BigDecimal("10.00"), new BigDecimal("0.0000"))),
                List.of(
                        new CanonicalAccountMonthlyDebitCreditTotals("1", "100", java.time.YearMonth.from(start), "LN", new BigDecimal("10"), BigDecimal.ZERO),
                        new CanonicalAccountMonthlyDebitCreditTotals("1", "100", java.time.YearMonth.from(start), "LN", new BigDecimal("10.0000"), new BigDecimal("0.00"))),
                null,
                null,
                List.of(
                        new CanonicalEntryCostAllocation("e1", "100", "CC1", "D", new BigDecimal("10.0")),
                        new CanonicalEntryCostAllocation("e1", "100", "CC1", "D", new BigDecimal("10.000"))));

        assertThatCode(() -> CanonicalV2BatchValidator.validate(batch, start, end, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNegativeDebitOrCreditTotalsEvenForSignedMovementSources() {
        var caps = capabilities(Set.of(AccountingDataset.DAILY_TOTALS), AmountSignPolicy.SIGNED_WITH_SIDE);
        var batch = new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                caps,
                null,
                null,
                List.of(new CanonicalAccountDailyDebitCreditTotals(
                        "1", "100", start, "LN", new BigDecimal("-1"), BigDecimal.ZERO)),
                null,
                null,
                null,
                null);

        assertInvalid(batch);
    }

    private CanonicalAccountingBatch batch(
            SourceCapabilities capabilities,
            CanonicalChartSnapshot chart,
            CanonicalEntriesDataset entries) {
        List<CanonicalObservedAccountDescriptor> observed = null;
        if (chart == null && entries != null && entries.entries() != null) {
            var byId = new java.util.LinkedHashMap<String, CanonicalObservedAccountDescriptor>();
            for (var entry : entries.entries()) {
                if (entry != null && entry.debitAccountId() != null) {
                    byId.putIfAbsent(
                            entry.debitAccountId(),
                            new CanonicalObservedAccountDescriptor(
                                    entry.debitAccountId(), "1", "Observed " + entry.debitAccountId()));
                }
                if (entry != null && entry.creditAccountId() != null) {
                    byId.putIfAbsent(
                            entry.creditAccountId(),
                            new CanonicalObservedAccountDescriptor(
                                    entry.creditAccountId(), "1", "Observed " + entry.creditAccountId()));
                }
            }
            observed = List.copyOf(byId.values());
        }

        return new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                identity(),
                capabilities,
                chart,
                observed,
                entries,
                null,
                null,
                null,
                null,
                null);
    }

    private SourceCapabilities capabilities(Set<AccountingDataset> datasets, AmountSignPolicy policy) {
        return new SourceCapabilities(datasets, policy, true);
    }

    private CanonicalEntriesDataset entries(CanonicalAccountingEntry... values) {
        return new CanonicalEntriesDataset(start, end, List.of(values), null);
    }

    private CanonicalChartSnapshot chart(CanonicalAccountingAccount... accounts) {
        return new CanonicalChartSnapshot(Instant.parse("2026-07-31T12:00:00Z"), List.of(accounts));
    }

    private CanonicalAccountingAccount account(String id, String classification, String description) {
        return new CanonicalAccountingAccount(
                id,
                classification,
                description,
                new SourceAccountCodes("2", "1", "1", "1", "2", null, null));
    }

    private ExternalCompanyIdentity identity() {
        return new ExternalCompanyIdentity("57", "52525252000152");
    }

    private void assertInvalid(CanonicalAccountingBatch batch) {
        assertThatThrownBy(() -> CanonicalV2BatchValidator.validate(batch, start, end, 100))
                .isInstanceOfSatisfying(
                        SyncPublicationRejectedException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(SyncFailureCode.INVALID_PAYLOAD));
    }

    private void assertConflict(CanonicalAccountingBatch batch) {
        assertThatThrownBy(() -> CanonicalV2BatchValidator.validate(batch, start, end, 100))
                .isInstanceOfSatisfying(
                        SyncPublicationRejectedException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(SyncFailureCode.DATA_CONFLICT));
    }
}
