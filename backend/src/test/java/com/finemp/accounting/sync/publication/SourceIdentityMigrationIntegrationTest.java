package com.finemp.accounting.sync.publication;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class SourceIdentityMigrationIntegrationTest {
    @Test void shouldRefuseLegacyDuplicatesWithoutDeletingOrOverwritingAnyAccountingValue() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("18").load().migrate();
            var company = UUID.randomUUID(); var source = UUID.randomUUID();
            var firstAccount = UUID.randomUUID(); var secondAccount = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO companies(id, legal_name, cnpj) VALUES ('" + company + "', 'Synthetic Legacy', '33333333000133')");
                sql.executeUpdate("INSERT INTO accounting_sources(id, company_id, type, external_reference, active) VALUES ('" + source + "', '" + company + "', 'QUESTOR', 'synthetic', true)");
                sql.executeUpdate("INSERT INTO accounting_accounts(id, accounting_source_id, external_account_id, account_key, description) VALUES ('" + firstAccount + "', '" + source + "', 'first', 'first', 'Synthetic')");
                sql.executeUpdate("INSERT INTO accounting_accounts(id, accounting_source_id, external_account_id, account_key, description) VALUES ('" + secondAccount + "', '" + source + "', 'second', 'second', 'Synthetic')");
                sql.executeUpdate("INSERT INTO accounting_movements(id, account_id, source_entry_key, side, accounting_date, amount) VALUES ('" + UUID.randomUUID() + "', '" + firstAccount + "', 'duplicate', 'DEBIT', DATE '2026-07-01', 1.2)");
                sql.executeUpdate("INSERT INTO accounting_movements(id, account_id, source_entry_key, side, accounting_date, amount) VALUES ('" + UUID.randomUUID() + "', '" + secondAccount + "', 'duplicate', 'DEBIT', DATE '2026-07-02', 2.3)");
            }
            assertThatThrownBy(() -> Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate())
                    .isInstanceOf(FlywayException.class);
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement();
                 var rows = sql.executeQuery("SELECT COUNT(*), SUM(amount) FROM accounting_movements")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(2L);
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(new BigDecimal("3.5000"));
            }
        }
    }
}

