package com.finemp.accounting.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class AccountingDatasetCoverageMigrationIntegrationTest {

    @Test
    void shouldBackfillOnlySucceededMovementCoverageAndPreserveHistoricalGaps() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("58"))
                    .load()
                    .migrate();

            UUID companyId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var company = connection.prepareStatement(
                        "INSERT INTO companies(id, legal_name, cnpj) VALUES (?, ?, ?)")) {
                    company.setObject(1, companyId);
                    company.setString(2, "Synthetic Coverage Migration");
                    company.setString(3, "75000000000175");
                    company.executeUpdate();
                }
                try (var source = connection.prepareStatement(
                        """
                        INSERT INTO accounting_sources(id, company_id, type, external_reference, active, connection_mode)
                        VALUES (?, ?, 'QUESTOR', 'coverage-migration', TRUE, 'DIRECT')
                        """)) {
                    source.setObject(1, sourceId);
                    source.setObject(2, companyId);
                    source.executeUpdate();
                }

                succeeded(connection, sourceId, "2026-01-01", "2026-01-10");
                succeeded(connection, sourceId, "2026-01-11", "2026-01-20");
                succeeded(connection, sourceId, "2026-02-01", "2026-02-10");
                succeeded(connection, sourceId, "2026-02-05", "2026-02-28");
                failed(connection, sourceId, "2026-01-21", "2026-01-31");
            }

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var query = connection.prepareStatement(
                         """
                         SELECT dataset, establishment_code, imported_from, imported_to, published_at
                         FROM accounting_dataset_coverage
                         WHERE accounting_source_id = ?
                         ORDER BY imported_from, imported_to
                         """)) {
                query.setObject(1, sourceId);
                try (var result = query.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("dataset")).isEqualTo("MOVEMENTS");
                    assertThat(result.getString("establishment_code")).isNull();
                    assertThat(result.getObject("published_at")).isNull();
                    assertThat(result.getObject("imported_from", LocalDate.class)).isEqualTo(LocalDate.of(2026, 1, 1));
                    assertThat(result.getObject("imported_to", LocalDate.class)).isEqualTo(LocalDate.of(2026, 1, 20));

                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("dataset")).isEqualTo("MOVEMENTS");
                    assertThat(result.getString("establishment_code")).isNull();
                    assertThat(result.getObject("published_at")).isNull();
                    assertThat(result.getObject("imported_from", LocalDate.class)).isEqualTo(LocalDate.of(2026, 2, 1));
                    assertThat(result.getObject("imported_to", LocalDate.class)).isEqualTo(LocalDate.of(2026, 2, 28));

                    assertThat(result.next()).isFalse();
                }
            }
        }
    }

    private void succeeded(java.sql.Connection connection, UUID sourceId, String from, String to) throws Exception {
        insertRun(connection, sourceId, from, to, "SUCCEEDED", null, 0L, 0L, 0L);
    }

    private void failed(java.sql.Connection connection, UUID sourceId, String from, String to) throws Exception {
        insertRun(connection, sourceId, from, to, "FAILED", "READ_FAILED", null, null, null);
    }

    private void insertRun(
            java.sql.Connection connection,
            UUID sourceId,
            String from,
            String to,
            String status,
            String failureCode,
            Long readCount,
            Long importedCount,
            Long skippedCount) throws Exception {
        try (var insert = connection.prepareStatement(
                """
                INSERT INTO sync_runs(
                    id, accounting_source_id, requested_by_user_id, idempotency_key, mode, status,
                    start_date, end_date, requested_at, started_at, finished_at,
                    read_count, imported_count, skipped_count, failure_code, version, attempt_number)
                VALUES (?, ?, ?, ?, 'INITIAL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
                """)) {
            var requested = java.time.OffsetDateTime.parse("2026-04-01T10:00:00Z");
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, sourceId);
            insert.setObject(3, UUID.randomUUID());
            insert.setObject(4, UUID.randomUUID());
            insert.setString(5, status);
            insert.setObject(6, LocalDate.parse(from));
            insert.setObject(7, LocalDate.parse(to));
            insert.setObject(8, requested);
            insert.setObject(9, requested.plusMinutes(1));
            insert.setObject(10, requested.plusMinutes(2));
            insert.setObject(11, readCount);
            insert.setObject(12, importedCount);
            insert.setObject(13, skippedCount);
            insert.setString(14, failureCode);
            insert.executeUpdate();
        }
    }
}
