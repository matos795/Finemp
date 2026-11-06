package com.finemp.production;

import com.finemp.identity.AccountPasswordPolicy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

final class ProductionFirstAdminPasswordReset {

    private static final Set<String> PLATFORM_PERMISSIONS = Set.of(
            "ACCOUNTING_FIRM_CREATE",
            "ACCOUNTING_FIRM_VIEW",
            "UNASSIGNED_COMPANY_ASSIGN",
            "PLATFORM_USER_MANAGE");

    private ProductionFirstAdminPasswordReset() {
    }

    static boolean reset(
            Connection connection,
            String email,
            String newPassword,
            PasswordEncoder passwordEncoder) throws SQLException {

        if (!connection.getAutoCommit()
                || email == null
                || email.isBlank()
                || !AccountPasswordPolicy.isValid(newPassword)
                || passwordEncoder == null) {
            throw new IllegalArgumentException("Invalid first-admin password reset context");
        }

        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);

        try {
            execute(connection, "SET LOCAL lock_timeout='10s'");
            execute(connection, "SET LOCAL statement_timeout='30s'");
            execute(connection, "SELECT pg_advisory_xact_lock(534826300)");
            execute(connection, """
                    LOCK TABLE
                        user_accounts,
                        user_account_platform_permissions,
                        accounting_firm_access_grants,
                        company_access_grants
                    IN SHARE ROW EXCLUSIVE MODE
                    """);

            require(connection, "SELECT count(*) FROM user_accounts", 1);
            require(connection, """
                    SELECT count(*)
                    FROM user_accounts
                    WHERE lower(email)=lower(?)
                      AND active=true
                      AND invitation_pending=false
                    """, 1, email);

            require(connection, """
                    SELECT count(*)
                    FROM user_account_platform_permissions p
                    JOIN user_accounts u ON u.id=p.user_account_id
                    WHERE lower(u.email)=lower(?)
                      AND p.permission IN (?,?,?,?)
                    """,
                    PLATFORM_PERMISSIONS.size(),
                    email,
                    "ACCOUNTING_FIRM_CREATE",
                    "ACCOUNTING_FIRM_VIEW",
                    "UNASSIGNED_COMPANY_ASSIGN",
                    "PLATFORM_USER_MANAGE");

            require(
                    connection,
                    "SELECT count(*) FROM user_account_platform_permissions",
                    PLATFORM_PERMISSIONS.size());
            require(connection, "SELECT count(*) FROM accounting_firm_access_grants", 0);
            require(connection, "SELECT count(*) FROM company_access_grants", 0);

            var current = loadCurrentCredentials(connection, email);

            if (passwordEncoder.matches(newPassword, current.passwordHash())) {
                connection.commit();
                return false;
            }

            String newHash = passwordEncoder.encode(newPassword);

            execute(connection, """
                    UPDATE user_accounts
                    SET password_hash=?,
                        credentials_version=credentials_version+1
                    WHERE id=?
                    """, newHash, current.userId());

            connection.commit();
            return true;
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static CurrentCredentials loadCurrentCredentials(
            Connection connection,
            String email) throws SQLException {

        try (var statement = connection.prepareStatement("""
                SELECT id, password_hash
                FROM user_accounts
                WHERE lower(email)=lower(?)
                  AND active=true
                  AND invitation_pending=false
                """)) {
            statement.setString(1, email);

            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("First-admin password reset precondition failed");
                }

                UUID userId = row.getObject("id", UUID.class);
                String passwordHash = row.getString("password_hash");

                if (row.next()
                        || passwordHash == null
                        || !passwordHash.matches("\\{bcrypt\\}\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) {
                    throw new SQLException("First-admin password reset precondition failed");
                }

                return new CurrentCredentials(userId, passwordHash);
            }
        }
    }

    private static long count(
            Connection connection,
            String sql,
            Object... args) throws SQLException {

        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);

            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void require(
            Connection connection,
            String sql,
            long expected,
            Object... args) throws SQLException {

        if (count(connection, sql, args) != expected) {
            throw new SQLException("First-admin password reset precondition failed");
        }
    }

    private static void execute(
            Connection connection,
            String sql,
            Object... args) throws SQLException {

        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.execute();
        }
    }

    private static void bind(
            java.sql.PreparedStatement statement,
            Object[] args) throws SQLException {

        for (int index = 0; index < args.length; index++) {
            statement.setObject(index + 1, args[index]);
        }
    }

    private record CurrentCredentials(UUID userId, String passwordHash) {
    }
}
