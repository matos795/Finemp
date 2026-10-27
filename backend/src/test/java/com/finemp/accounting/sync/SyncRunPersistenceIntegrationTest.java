package com.finemp.accounting.sync;

import static org.assertj.core.api.Assertions.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.source.*;
import com.finemp.company.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SyncRunPersistenceIntegrationTest {
    @Autowired private SyncRunRepository runs;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");

    @Test void shouldPersistMetricsAndNeverFindAnotherSourcesRunThroughScopedQueries() {
        var source = source(); var other = source();
        var run = run(source, UUID.randomUUID());
        run.start(now); run.succeed(3, 2, 1, now);
        runs.saveAndFlush(run); var id = run.getId(); em.clear();
        var actual = runs.findByIdAndAccountingSourceId(id, source.getId()).orElseThrow();
        assertThat(actual.getReadCount()).isEqualTo(3);
        assertThat(actual.getImportedCount()).isEqualTo(2);
        assertThat(actual.getSkippedCount()).isEqualTo(1);
        assertThat(runs.findByIdAndAccountingSourceId(id, other.getId())).isEmpty();
        assertThat(runs.findByAccountingSourceIdAndIdempotencyKey(other.getId(), run.getIdempotencyKey())).isEmpty();
    }

    @Test void shouldEnforceOneActiveRunPerSourceInPostgres() {
        var source = source();
        runs.saveAndFlush(run(source, UUID.randomUUID()));
        assertThatThrownBy(() -> runs.saveAndFlush(run(source, UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void shouldKeepIdempotencyKeyUniqueAfterCompletion() {
        var source = source(); var key = UUID.randomUUID(); var first = run(source, key);
        first.cancel(now); runs.saveAndFlush(first);
        assertThatThrownBy(() -> runs.saveAndFlush(run(source, key))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void shouldAllowNewRequestAfterTerminalStateAndPageOnlyTheSource() {
        var source = source(); var other = source();
        var cancelled = run(source, UUID.randomUUID()); cancelled.cancel(now); runs.saveAndFlush(cancelled);
        runs.saveAndFlush(run(source, UUID.randomUUID()));
        runs.saveAndFlush(run(other, UUID.randomUUID()));
        assertThat(runs.existsByAccountingSourceIdAndStatusIn(source.getId(), Set.of(SyncRunStatus.REQUESTED, SyncRunStatus.RUNNING))).isTrue();
        var page = runs.findAllByAccountingSourceIdAndStatus(source.getId(), SyncRunStatus.CANCELLED,
                PageRequest.of(0, 1, Sort.by("requestedAt").descending().and(Sort.by("id"))));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(SyncRun::getId).containsExactly(cancelled.getId());
    }

    @Test void shouldRejectInconsistentCountersEvenIfAnInternalCallerBypassesTheEntity() {
        var run = runs.saveAndFlush(run(source(), UUID.randomUUID()));
        assertThatThrownBy(() -> jdbc.update("UPDATE sync_runs SET status = 'SUCCEEDED', started_at = requested_at,"
                + " finished_at = requested_at, read_count = 5, imported_count = 3, skipped_count = 1 WHERE id = ?", run.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AccountingSource source() {
        return sources.saveAndFlush(new AccountingSource(companies.saveAndFlush(new Company("Synthetic Sync", "20202020000120")),
                AccountingSourceType.QUESTOR, "synthetic", true));
    }
    private SyncRun run(AccountingSource source, UUID key) {
        return new SyncRun(source, UUID.randomUUID(), key, SyncRunMode.INITIAL,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), now);
    }
}
