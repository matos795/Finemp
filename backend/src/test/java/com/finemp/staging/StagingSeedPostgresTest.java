package com.finemp.staging;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class StagingSeedPostgresTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
    static final String HASH=StagingProvisioningMain.encodePassword("Synthetic-only-password");
    @BeforeAll static void schema(){Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load().migrate();}
    Connection connect() throws SQLException {return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());}
    @AfterEach void cleanup() throws Exception {try(var c=connect();var s=c.createStatement()){
        s.execute("DROP TRIGGER IF EXISTS reject_seed ON staging_seed_receipts");s.execute("DROP FUNCTION IF EXISTS reject_seed()");
        for(var table:List.of("staging_seed_receipts","company_access_grant_permissions","company_access_grants","financial_categories","financial_bank_accounts","companies","clients","accounting_firms","user_accounts"))s.executeUpdate("DELETE FROM "+table);
    }}
    long count(Connection c,String table) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table)){r.next();return r.getLong(1);}}
    @Test void createsMinimumAndRepeatNeverChangesPasswordOrGrants() throws Exception {
        try(var c=connect()){
            assertThat(StagingSeed.provision(c,HASH)).isTrue();
            assertThat(count(c,"companies")).isEqualTo(1);assertThat(count(c,"user_accounts")).isEqualTo(1);
            assertThat(count(c,"company_access_grant_permissions")).isEqualTo(3);assertThat(count(c,"user_account_platform_permissions")).isZero();
            assertThat(count(c,"financial_categories")).isEqualTo(4);assertThat(count(c,"financial_entries")).isZero();
            try(var s=c.createStatement()){s.executeUpdate("DELETE FROM company_access_grant_permissions WHERE permission='BANK_ACCOUNT_VIEW'");}
            assertThat(StagingSeed.provision(c,PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("Another-synthetic-password"))).isFalse();
            assertThat(count(c,"company_access_grant_permissions")).isEqualTo(2);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT password_hash FROM user_accounts")){r.next();assertThat(r.getString(1)).isEqualTo(HASH);assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("Synthetic-only-password",r.getString(1))).isTrue();}
        }
    }
    @Test void refusesNonemptyUnmarkedDatabase() throws Exception {
        try(var c=connect();var s=c.prepareStatement("INSERT INTO accounting_firms(id,name,active) VALUES(?,'Existing synthetic firm',true)")){
            s.setObject(1,UUID.randomUUID());s.executeUpdate();
            assertThatThrownBy(()->StagingSeed.provision(c,HASH)).isInstanceOf(SQLException.class);
            assertThat(count(c,"accounting_firms")).isEqualTo(1);assertThat(count(c,"user_accounts")).isZero();
        }
    }
    @Test void lateFailureRollsBackEveryCreatedRecord() throws Exception {
        try(var c=connect();var s=c.createStatement()){
            s.execute("CREATE FUNCTION reject_seed() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic failure'; END; $$");
            s.execute("CREATE TRIGGER reject_seed BEFORE INSERT ON staging_seed_receipts FOR EACH ROW EXECUTE FUNCTION reject_seed()");
            assertThatThrownBy(()->StagingSeed.provision(c,HASH)).isInstanceOf(SQLException.class);
            for(var table:List.of("user_accounts","companies","clients","accounting_firms","financial_categories","financial_bank_accounts","company_access_grants"))assertThat(count(c,table)).isZero();
        }
    }
    @Test void concurrentCommandsCreateOneDataset() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            Callable<Boolean> action=()->{try(var c=connect()){return StagingSeed.provision(c,HASH);}};
            var first=pool.submit(action);var second=pool.submit(action);
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        try(var c=connect()){assertThat(count(c,"staging_seed_receipts")).isEqualTo(1);assertThat(count(c,"companies")).isEqualTo(1);}
    }

    @Test void resetPasswordRequiresExistingSyntheticSeed() throws Exception {
        String newHash=StagingProvisioningMain.encodePassword("New-synthetic-password");
        try(var c=connect()) {
            assertThatThrownBy(()->StagingSeed.resetSyntheticPassword(c,newHash)).isInstanceOf(SQLException.class);
            assertThat(count(c,"user_accounts")).isZero();
        }
    }

    @Test void resetPasswordChangesOnlySyntheticCredentialsAndInvalidatesSessions() throws Exception {
        String newPassword="New-synthetic-password";
        String newHash=StagingProvisioningMain.encodePassword(newPassword);
        try(var c=connect()) {
            StagingSeed.provision(c,HASH);
            long grants=count(c,"company_access_grants");
            long permissions=count(c,"company_access_grant_permissions");

            StagingSeed.resetSyntheticPassword(c,newHash);

            assertThat(count(c,"companies")).isEqualTo(1);
            assertThat(count(c,"company_access_grants")).isEqualTo(grants);
            assertThat(count(c,"company_access_grant_permissions")).isEqualTo(permissions);
            try(var s=c.prepareStatement("SELECT password_hash,credentials_version,email,active,invitation_pending FROM user_accounts WHERE id=?")) {
                s.setObject(1,StagingSeed.id("user"));
                try(var r=s.executeQuery()) {
                    assertThat(r.next()).isTrue();
                    assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(newPassword,r.getString("password_hash"))).isTrue();
                    assertThat(r.getLong("credentials_version")).isEqualTo(1);
                    assertThat(r.getString("email")).isEqualTo("demo@finemp.example.invalid");
                    assertThat(r.getBoolean("active")).isTrue();
                    assertThat(r.getBoolean("invitation_pending")).isFalse();
                }
            }
        }
    }

    @Test void resetPasswordRefusesChangedSyntheticIdentityWithoutMutatingCredentials() throws Exception {
        String newHash=StagingProvisioningMain.encodePassword("New-synthetic-password");
        try(var c=connect();var s=c.createStatement()) {
            StagingSeed.provision(c,HASH);
            s.executeUpdate("UPDATE user_accounts SET email='changed@finemp.example.invalid' WHERE id='"+StagingSeed.id("user")+"'");
            assertThatThrownBy(()->StagingSeed.resetSyntheticPassword(c,newHash)).isInstanceOf(SQLException.class);
            try(var r=s.executeQuery("SELECT password_hash,credentials_version FROM user_accounts WHERE id='"+StagingSeed.id("user")+"'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString("password_hash")).isEqualTo(HASH);
                assertThat(r.getLong("credentials_version")).isZero();
            }
        }
    }

    @Test void isolationCompanyRequiresSeedAndIsIdempotentWithoutGrants() throws Exception {
        try(var c=connect()) {
            assertThatThrownBy(()->StagingSeed.provisionIsolationCompany(c)).isInstanceOf(SQLException.class);
            assertThat(count(c,"companies")).isZero();
            StagingSeed.provision(c,HASH);
            assertThat(StagingSeed.provisionIsolationCompany(c)).isTrue();
            assertThat(StagingSeed.provisionIsolationCompany(c)).isFalse();
            assertThat(count(c,"companies")).isEqualTo(2);
            assertThat(count(c,"company_access_grants")).isEqualTo(1);
            assertThat(count(c,"user_accounts")).isEqualTo(1);
            assertThat(count(c,"staging_seed_receipts")).isEqualTo(2);
        }
    }
    @Test void isolationCompanyRollsBackWhenReceiptCannotBeWritten() throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            StagingSeed.provision(c,HASH);
            s.execute("CREATE FUNCTION reject_seed() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic failure'; END; $$");
            s.execute("CREATE TRIGGER reject_seed BEFORE INSERT ON staging_seed_receipts FOR EACH ROW EXECUTE FUNCTION reject_seed()");
            assertThatThrownBy(()->StagingSeed.provisionIsolationCompany(c)).isInstanceOf(SQLException.class);
            assertThat(count(c,"companies")).isEqualTo(1);
            assertThat(count(c,"staging_seed_receipts")).isEqualTo(1);
        }
    }
    @Test void isolationRetryRefusesChangedAccessWithoutRevokingIt() throws Exception {
        try(var c=connect();var s=c.prepareStatement("INSERT INTO company_access_grants(id,user_account_id,company_id,access_role,cost_center_scope) VALUES(?,?,?,'CUSTOM','ALL')")) {
            StagingSeed.provision(c,HASH);StagingSeed.provisionIsolationCompany(c);
            s.setObject(1,UUID.randomUUID());s.setObject(2,StagingSeed.id("user"));s.setObject(3,StagingSeed.id("foreign-company"));s.executeUpdate();
            assertThatThrownBy(()->StagingSeed.provisionIsolationCompany(c)).isInstanceOf(SQLException.class);
            assertThat(count(c,"company_access_grants")).isEqualTo(2);
        }
    }
}
