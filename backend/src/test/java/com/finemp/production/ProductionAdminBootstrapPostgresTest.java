package com.finemp.production;

import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ProductionAdminBootstrapPostgresTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
    static final String HASH=PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("Test-password-123");

    @BeforeAll static void schema() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load().migrate();
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
    }

    @AfterEach void cleanup() throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            for(var table: List.of(
                    "user_account_platform_permissions",
                    "accounting_firm_access_grant_permissions",
                    "accounting_firm_access_grants",
                    "company_access_grant_permissions",
                    "company_access_grants",
                    "user_accounts")) {
                s.executeUpdate("DELETE FROM "+table);
            }
        }
    }

    long count(Connection c,String table) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table)) {
            r.next();
            return r.getLong(1);
        }
    }

    @Test void createsOnlyFirstPlatformAdministratorAndIsIdempotent() throws Exception {
        try(var c=connect()) {
            assertThat(ProductionAdminBootstrap.provision(c,"FINEMP Admin","admin@example.com",HASH)).isTrue();
            assertThat(ProductionAdminBootstrap.provision(c,"FINEMP Admin","admin@example.com",HASH)).isFalse();
            assertThat(count(c,"user_accounts")).isEqualTo(1);
            assertThat(count(c,"user_account_platform_permissions")).isEqualTo(4);
            assertThat(count(c,"accounting_firm_access_grants")).isZero();
            assertThat(count(c,"company_access_grants")).isZero();
            assertThat(count(c,"companies")).isZero();
        }
    }

    @Test void refusesOccupiedOrDivergentIdentity() throws Exception {
        try(var c=connect()) {
            ProductionAdminBootstrap.provision(c,"FINEMP Admin","admin@example.com",HASH);
            assertThatThrownBy(() -> ProductionAdminBootstrap.provision(c,"Other Admin","admin@example.com",HASH))
                    .isInstanceOf(SQLException.class);
            try(var s=c.createStatement()) {
                s.executeUpdate("DELETE FROM user_account_platform_permissions");
            }
            assertThatThrownBy(() -> ProductionAdminBootstrap.provision(c,"FINEMP Admin","admin@example.com",HASH))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test void concurrentBootstrapsCreateOneAdministrator() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> action=()->{
                try(var c=connect()) {
                    return ProductionAdminBootstrap.provision(c,"FINEMP Admin","admin@example.com",HASH);
                }
            };
            var first=pool.submit(action);
            var second=pool.submit(action);
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
        try(var c=connect()) {
            assertThat(count(c,"user_accounts")).isEqualTo(1);
            assertThat(count(c,"user_account_platform_permissions")).isEqualTo(4);
        }
    }
}
