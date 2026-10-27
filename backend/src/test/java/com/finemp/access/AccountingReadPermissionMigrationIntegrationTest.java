package com.finemp.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class AccountingReadPermissionMigrationIntegrationTest {
    @Test
    void shouldEncodeOnlyLegacyReadCapabilitiesAndNeverGrantThemToNewEmptyBindings() throws Exception {
        // Dedicated disposable container, never the application's configured database.
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("14").load().migrate();
            UUID companyId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID legacyId = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO companies(id, legal_name, cnpj) VALUES ('" + companyId + "', 'Synthetic', '14141414000114')");
                statement.executeUpdate("INSERT INTO user_accounts(id, name, email, password_hash) VALUES ('" + userId + "', 'Synthetic', 'synthetic@example.invalid', 'hash')");
                statement.executeUpdate("INSERT INTO company_access_grants(id, user_account_id, company_id) VALUES ('" + legacyId + "', '" + userId + "', '" + companyId + "')");
            }
            var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
            flyway.migrate();
            flyway.migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                var actual = new HashSet<String>();
                try (var result = statement.executeQuery("SELECT permission FROM company_access_grant_permissions WHERE company_access_grant_id = '" + legacyId + "'")) {
                    while (result.next()) actual.add(result.getString(1));
                }
                assertThat(actual).isEqualTo(Set.of("ACCOUNTING_MOVEMENT_VIEW", "ACCOUNT_CLASSIFICATION_VIEW"));
                var newUser = UUID.randomUUID();
                var newGrant = UUID.randomUUID();
                statement.executeUpdate("INSERT INTO user_accounts(id, name, email, password_hash) VALUES ('" + newUser + "', 'New Synthetic', 'new@example.invalid', 'hash')");
                statement.executeUpdate("INSERT INTO company_access_grants(id, user_account_id, company_id) VALUES ('" + newGrant + "', '" + newUser + "', '" + companyId + "')");
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM company_access_grant_permissions WHERE company_access_grant_id = '" + newGrant + "'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isZero();
                }
            }
        }
    }
}
