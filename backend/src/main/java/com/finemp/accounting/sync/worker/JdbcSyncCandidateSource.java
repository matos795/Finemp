package com.finemp.accounting.sync.worker;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Internal bounded candidate lookup. A candidate is not authorization or ownership of a claim. */
public class JdbcSyncCandidateSource implements SyncCandidateSource {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public JdbcSyncCandidateSource(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Override public List<UUID> due(int limit, Set<UUID> excluded) {
        if (limit < 1 || limit > 8 || excluded == null || excluded.size() > 8) throw new IllegalArgumentException("Invalid worker candidate limit.");
        var now = Timestamp.from(clock.instant());
        var exclusions = List.copyOf(excluded);
        String exclusionSql = exclusions.isEmpty() ? "" : " AND run.id NOT IN (" + String.join(",", Collections.nCopies(exclusions.size(), "?")) + ") ";
        String sql = """
                SELECT run.id FROM sync_runs run
                JOIN accounting_sources source ON source.id = run.accounting_source_id
                LEFT JOIN sync_attempts attempt ON attempt.sync_run_id = run.id AND attempt.attempt_number = run.attempt_number
                WHERE source.connection_mode = 'DIRECT' AND run.status IN ('REQUESTED', 'RUNNING') AND (run.status = 'REQUESTED' OR (run.status = 'RUNNING' AND
                    (attempt.id IS NULL OR (attempt.status = 'RUNNING' AND attempt.lease_expires_at <= ?)
                    OR (attempt.status IN ('FAILED', 'EXPIRED') AND attempt.next_attempt_at <= ?))))
                """ + exclusionSql + """
                 ORDER BY COALESCE(attempt.next_attempt_at, attempt.lease_expires_at, run.requested_at), run.id
                LIMIT ?
                """;
        return jdbc.query(sql, statement -> { statement.setQueryTimeout(5); statement.setTimestamp(1, now);
                    statement.setTimestamp(2, now); int parameter = 3;
                    for (var id : exclusions) statement.setObject(parameter++, id);
                    statement.setInt(parameter, limit); },
                (rows, index) -> rows.getObject(1, UUID.class));
    }
}
