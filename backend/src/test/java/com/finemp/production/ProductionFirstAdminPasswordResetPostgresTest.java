package com.finemp.production;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ProductionFirstAdminPasswordResetPostgresTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final PasswordEncoder PASSWORD_ENCODER =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    static final String OLD_PASSWORD = "Old-password-123";
    static final String NEW_PASSWORD = "New-password-456";
    static final String EMAIL = "admin@example.com";

    @BeforeAll
    static void schema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
    }

    @AfterEach
    void cleanup() throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM user_account_platform_permissions");
            statement.executeUpdate("DELETE FROM accounting_firm_access_grant_permissions");
            statement.executeUpdate("DELETE FROM accounting_firm_access_grants");
            statement.executeUpdate("DELETE FROM company_access_grant_permissions");
            statement.executeUpdate("DELETE FROM company_access_grants");
            statement.executeUpdate("DELETE FROM user_accounts");
        }
    }

    @Test
    void resetsOnlyTheFirstPlatformAdministratorAndInvalidatesExistingSessions() throws Exception {
        createFirstAdmin();

        try (var connection = connect()) {
            assertThat(ProductionFirstAdminPasswordReset.reset(
                    connection, EMAIL, NEW_PASSWORD, PASSWORD_ENCODER)).isTrue();
        }

        try (var connection = connect();
             var statement = connection.prepareStatement("""
                     SELECT password_hash, credentials_version
                     FROM user_accounts
                     WHERE lower(email)=lower(?)
                     """)) {
            statement.setString(1, EMAIL);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(PASSWORD_ENCODER.matches(NEW_PASSWORD, row.getString("password_hash"))).isTrue();
                assertThat(PASSWORD_ENCODER.matches(OLD_PASSWORD, row.getString("password_hash"))).isFalse();
                assertThat(row.getLong("credentials_version")).isEqualTo(1);
            }
        }
    }

    @Test
    void isIdempotentWhenTheRequestedPasswordAlreadyMatches() throws Exception {
        createFirstAdmin();

        try (var connection = connect()) {
            assertThat(ProductionFirstAdminPasswordReset.reset(
                    connection, EMAIL, OLD_PASSWORD, PASSWORD_ENCODER)).isFalse();
        }

        try (var connection = connect();
             var statement = connection.prepareStatement("""
                     SELECT credentials_version
                     FROM user_accounts
                     WHERE lower(email)=lower(?)
                     """)) {
            statement.setString(1, EMAIL);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("credentials_version")).isZero();
            }
        }
    }

    @Test
    void refusesWrongIdentityAndIncompletePermissions() throws Exception {
        createFirstAdmin();

        try (var connection = connect()) {
            assertThatThrownBy(() -> ProductionFirstAdminPasswordReset.reset(
                    connection, "other@example.com", NEW_PASSWORD, PASSWORD_ENCODER))
                    .isInstanceOf(SQLException.class);
        }

        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    DELETE FROM user_account_platform_permissions
                    WHERE permission='PLATFORM_USER_MANAGE'
                    """);
        }

        try (var connection = connect()) {
            assertThatThrownBy(() -> ProductionFirstAdminPasswordReset.reset(
                    connection, EMAIL, NEW_PASSWORD, PASSWORD_ENCODER))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void refusesPasswordOutsideTheAccountPolicy() throws Exception {
        createFirstAdmin();

        try (var connection = connect()) {
            assertThatThrownBy(() -> ProductionFirstAdminPasswordReset.reset(
                    connection, EMAIL, "onlyletters", PASSWORD_ENCODER))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void createFirstAdmin() throws Exception {
        try (var connection = connect()) {
            ProductionAdminBootstrap.provision(
                    connection,
                    "FINEMP Admin",
                    EMAIL,
                    PASSWORD_ENCODER.encode(OLD_PASSWORD));
        }
    }
}
