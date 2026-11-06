package com.finemp.production;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

final class ProductionAdminBootstrap {
    private static final Set<String> PLATFORM_PERMISSIONS = Set.of(
            "ACCOUNTING_FIRM_CREATE",
            "ACCOUNTING_FIRM_VIEW",
            "UNASSIGNED_COMPANY_ASSIGN",
            "PLATFORM_USER_MANAGE");

    static boolean provision(Connection connection, String name, String email, String passwordHash) throws SQLException {
        if (!connection.getAutoCommit()
                || name == null || name.isBlank()
                || email == null || email.isBlank()
                || passwordHash == null
                || !passwordHash.matches("\\{bcrypt\\}\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException("Invalid first-admin bootstrap context");
        }
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        try {
            execute(connection, "SET LOCAL lock_timeout='10s'");
            execute(connection, "SET LOCAL statement_timeout='30s'");
            execute(connection, "SELECT pg_advisory_xact_lock(534826299)");
            execute(connection, "LOCK TABLE user_accounts, user_account_platform_permissions IN SHARE ROW EXCLUSIVE MODE");

            long users = count(connection, "SELECT count(*) FROM user_accounts");
            if (users == 1) {
                require(connection, """
                        SELECT count(*) FROM user_accounts
                        WHERE lower(email)=lower(?) AND name=? AND active=true
                          AND invitation_pending=false
                        """, 1, email, name);
                require(connection, """
                        SELECT count(*) FROM user_account_platform_permissions p
                        JOIN user_accounts u ON u.id=p.user_account_id
                        WHERE lower(u.email)=lower(?) AND p.permission IN (?,?,?,?)
                        """, PLATFORM_PERMISSIONS.size(), email,
                        "ACCOUNTING_FIRM_CREATE", "ACCOUNTING_FIRM_VIEW",
                        "UNASSIGNED_COMPANY_ASSIGN", "PLATFORM_USER_MANAGE");
                require(connection, "SELECT count(*) FROM user_account_platform_permissions",
                        PLATFORM_PERMISSIONS.size());
                connection.commit();
                return false;
            }
            if (users != 0) throw new SQLException("First-admin bootstrap database is not empty");
            require(connection, "SELECT count(*) FROM user_account_platform_permissions", 0);
            require(connection, "SELECT count(*) FROM accounting_firm_access_grants", 0);
            require(connection, "SELECT count(*) FROM company_access_grants", 0);

            UUID userId = UUID.randomUUID();
            execute(connection, """
                    INSERT INTO user_accounts
                        (id,name,email,password_hash,active,invitation_pending,credentials_version,created_at)
                    VALUES (?,?,?,?,true,false,0,CURRENT_TIMESTAMP)
                    """, userId, name, email, passwordHash);
            for (String permission : PLATFORM_PERMISSIONS) {
                execute(connection, """
                        INSERT INTO user_account_platform_permissions(user_account_id,permission)
                        VALUES (?,?)
                        """, userId, permission);
            }
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static long count(Connection connection, String sql, Object... args) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void require(Connection connection, String sql, long expected, Object... args) throws SQLException {
        if (count(connection, sql, args) != expected) throw new SQLException("First-admin bootstrap precondition failed");
    }

    private static void execute(Connection connection, String sql, Object... args) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.execute();
        }
    }

    private static void bind(java.sql.PreparedStatement statement, Object[] args) throws SQLException {
        for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
    }
}
