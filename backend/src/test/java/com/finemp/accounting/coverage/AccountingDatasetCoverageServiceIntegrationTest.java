package com.finemp.accounting.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountingDatasetCoverageServiceIntegrationTest {

    @Autowired private AccountingDatasetCoverageService coverage;
    @Autowired private AccountingDatasetCoverageRepository repository;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void shouldMergeOverlapsAdjacencyAndBridgesWhilePreservingGaps() {
        var source = source("71000000000171");

        coverage.record(source.getId(), AccountingDataset.MOVEMENTS,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        coverage.record(source.getId(), AccountingDataset.MOVEMENTS,
                LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 31));

        assertThat(coverage.read(source.getId(), AccountingDataset.MOVEMENTS).intervals())
                .containsExactly(
                        interval("2026-01-01", "2026-01-10"),
                        interval("2026-01-20", "2026-01-31"));

        coverage.record(source.getId(), AccountingDataset.MOVEMENTS,
                LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 15));
        assertThat(coverage.read(source.getId(), AccountingDataset.MOVEMENTS).intervals())
                .containsExactly(
                        interval("2026-01-01", "2026-01-15"),
                        interval("2026-01-20", "2026-01-31"));

        coverage.record(source.getId(), AccountingDataset.MOVEMENTS,
                LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 19));

        var snapshot = coverage.read(source.getId(), AccountingDataset.MOVEMENTS);
        assertThat(snapshot.importedFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(snapshot.importedTo()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(snapshot.intervals()).containsExactly(interval("2026-01-01", "2026-01-31"));
    }

    @Test
    @Transactional
    void shouldReplayIdempotentlyAndKeepDatasetsAndSourcesIsolated() {
        var first = source("72000000000172");
        var second = source("73000000000173");
        var from = LocalDate.of(2026, 2, 1);
        var to = LocalDate.of(2026, 2, 28);

        coverage.record(first.getId(), AccountingDataset.MOVEMENTS, from, to);
        coverage.record(first.getId(), AccountingDataset.MOVEMENTS, from, to);
        coverage.record(first.getId(), AccountingDataset.CHART, from, to);
        coverage.record(second.getId(), AccountingDataset.MOVEMENTS, from, to);

        assertThat(repository.findAllByAccountingSourceIdAndDatasetOrderByImportedFromAscImportedToAsc(
                first.getId(), AccountingDataset.MOVEMENTS)).hasSize(1);
        assertThat(coverage.read(first.getId(), AccountingDataset.CHART).intervals()).hasSize(1);
        assertThat(coverage.read(second.getId(), AccountingDataset.MOVEMENTS).intervals()).hasSize(1);
    }

    @Test
    void shouldSerializeConcurrentAdjacentCoverageForTheSameSource() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        UUID[] ids = tx.execute(status -> {
            var company = companies.saveAndFlush(new Company("Synthetic Coverage Concurrent", "74000000000174"));
            var source = sources.saveAndFlush(
                    new AccountingSource(company, AccountingSourceType.QUESTOR, "coverage-concurrent", true));
            return new UUID[] {company.getId(), source.getId()};
        });

        UUID companyId = ids[0];
        UUID sourceId = ids[1];
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> recordInOwnTransaction(
                    tx, start, sourceId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10)));
            var second = pool.submit(() -> recordInOwnTransaction(
                    tx, start, sourceId, LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 20)));

            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);

            assertThat(coverage.read(sourceId, AccountingDataset.MOVEMENTS).intervals())
                    .containsExactly(interval("2026-03-01", "2026-03-20"));
        } finally {
            repository.deleteAll(repository.findAllByAccountingSourceIdAndDatasetOrderByImportedFromAscImportedToAsc(
                    sourceId, AccountingDataset.MOVEMENTS));
            sources.deleteById(sourceId);
            companies.deleteById(companyId);
        }
    }

    private void recordInOwnTransaction(
            TransactionTemplate tx,
            CountDownLatch start,
            UUID sourceId,
            LocalDate from,
            LocalDate to) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic coverage workers did not start.");
            }
            tx.executeWithoutResult(status ->
                    coverage.record(sourceId, AccountingDataset.MOVEMENTS, from, to));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private AccountingSource source(String cnpj) {
        var company = companies.saveAndFlush(new Company("Synthetic Coverage " + cnpj, cnpj));
        return sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.QUESTOR, "coverage-" + cnpj, true));
    }

    private AccountingDatasetCoverageSnapshot.Interval interval(String from, String to) {
        return new AccountingDatasetCoverageSnapshot.Interval(LocalDate.parse(from), LocalDate.parse(to));
    }
}
