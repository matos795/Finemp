package com.finemp.accounting.sync.execution;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class SyncLeaseMigrationIntegrationTest {
    @Test void shouldFencePreUpgradeWorkersAndKeepActiveWorkRecoverable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("19").load().migrate();
            var company = UUID.randomUUID(); var source = UUID.randomUUID(); var run = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO companies(id, legal_name, cnpj) VALUES ('" + company + "', 'Synthetic Lease Migration', '42424242000142')");
                sql.executeUpdate("INSERT INTO accounting_sources(id, company_id, type, external_reference, active) VALUES ('" + source + "', '" + company + "', 'QUESTOR', 'synthetic', true)");
                sql.executeUpdate("INSERT INTO sync_runs(id, accounting_source_id, requested_by_user_id, idempotency_key, mode, status, start_date, end_date, requested_at, started_at, version) VALUES ('"
                        + run + "', '" + source + "', '" + UUID.randomUUID() + "', '" + UUID.randomUUID()
                        + "', 'INITIAL', 'RUNNING', DATE '2026-07-01', DATE '2026-07-31', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour', 7)");
            }
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("20").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement();
                 var rows = sql.executeQuery("SELECT run.version, run.status, run.attempt_number, attempt.fence_version, attempt.status, attempt.lease_expires_at <= CURRENT_TIMESTAMP AS expired"
                         + " FROM sync_runs run JOIN sync_attempts attempt ON attempt.sync_run_id = run.id WHERE run.id = '" + run + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(8); assertThat(rows.getString(2)).isEqualTo("RUNNING");
                assertThat(rows.getInt(3)).isEqualTo(1); assertThat(rows.getLong(4)).isEqualTo(8);
                assertThat(rows.getString(5)).isEqualTo("RUNNING"); assertThat(rows.getBoolean(6)).isTrue();
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
