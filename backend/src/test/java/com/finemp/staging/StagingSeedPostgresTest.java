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
        s.execute("DROP TRIGGER IF EXISTS reject_platform ON user_account_platform_permissions");s.execute("DROP FUNCTION IF EXISTS reject_platform()");
        for(var table:List.of("user_account_platform_permissions","staging_seed_receipts","company_access_grant_permissions","company_access_grants","financial_categories","financial_bank_accounts","companies","clients","accounting_firms","user_accounts"))s.executeUpdate("DELETE FROM "+table);
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

    // #334 — elevação explícita do operador sintético a administrador de plataforma.
    static final List<String> PLATFORM=List.of("ACCOUNTING_FIRM_CREATE","ACCOUNTING_FIRM_VIEW","PLATFORM_USER_MANAGE","UNASSIGNED_COMPANY_ASSIGN");
    List<String> platform(Connection c) throws SQLException {
        var result=new ArrayList<String>();
        try(var s=c.prepareStatement("SELECT permission FROM user_account_platform_permissions WHERE user_account_id=? ORDER BY permission")){s.setObject(1,StagingSeed.id("user"));
            try(var r=s.executeQuery()){while(r.next())result.add(r.getString(1));}}
        return result;
    }
    String credentials(Connection c) throws SQLException {
        try(var s=c.prepareStatement("SELECT password_hash||'|'||credentials_version||'|'||email||'|'||active||'|'||invitation_pending FROM user_accounts WHERE id=?")){
            s.setObject(1,StagingSeed.id("user"));try(var r=s.executeQuery()){r.next();return r.getString(1);}}
    }
    String accessSnapshot(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("""
                SELECT (SELECT count(*) FROM companies)||'|'||(SELECT count(*) FROM clients)||'|'||(SELECT count(*) FROM accounting_firms)||'|'||
                (SELECT string_agg(g.id||':'||g.company_id||':'||g.access_role||':'||g.cost_center_scope,',' ORDER BY g.id) FROM company_access_grants g)||'|'||
                (SELECT string_agg(p.company_access_grant_id||':'||p.permission,',' ORDER BY p.permission) FROM company_access_grant_permissions p)||'|'||
                (SELECT count(*) FROM accounting_firm_access_grants)||'|'||(SELECT count(*) FROM financial_categories)||'|'||(SELECT count(*) FROM financial_bank_accounts)""")){
            r.next();return r.getString(1);}
    }
    void addPlatform(Connection c,String...permissions) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO user_account_platform_permissions(user_account_id,permission) VALUES(?,?)")){
            for(var permission:permissions){s.setObject(1,StagingSeed.id("user"));s.setString(2,permission);s.executeUpdate();}}
    }
    @Test void platformAdminRequiresTheSyntheticMarker() throws Exception {
        try(var c=connect()) {
            assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).isInstanceOf(SQLException.class);
            assertThat(count(c,"user_account_platform_permissions")).isZero();
            // Usuário com o mesmo ID/email, mas sem marcador da fixture, também é recusado.
            try(var s=c.prepareStatement("INSERT INTO user_accounts(id,name,email,password_hash,active) VALUES(?,'Unmarked synthetic','demo@finemp.example.invalid',?,true)")){
                s.setObject(1,StagingSeed.id("user"));s.setString(2,HASH);s.executeUpdate();}
            assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).isInstanceOf(SQLException.class);
            assertThat(count(c,"user_account_platform_permissions")).isZero();
        }
    }
    @Test void platformAdminGrantsExactlyTheFourPermissionsAndRetryIsNoop() throws Exception {
        try(var c=connect()) {
            StagingSeed.provision(c,HASH);
            String credentials=credentials(c),access=accessSnapshot(c);
            assertThat(StagingSeed.grantSyntheticPlatformAdmin(c)).isTrue();
            assertThat(platform(c)).containsExactlyElementsOf(PLATFORM);
            assertThat(count(c,"user_account_platform_permissions")).isEqualTo(4);
            assertThat(StagingSeed.grantSyntheticPlatformAdmin(c)).isFalse();
            assertThat(platform(c)).containsExactlyElementsOf(PLATFORM);
            assertThat(credentials(c)).isEqualTo(credentials);
            assertThat(accessSnapshot(c)).isEqualTo(access);
            assertThat(count(c,"accounting_firm_access_grants")).isZero();
            assertThat(count(c,"user_accounts")).isEqualTo(1);
            assertThat(c.getAutoCommit()).isTrue();
        }
    }
    @Test void platformAdminRefusesPartialOrExtraPermissionsWithoutRepairingThem() throws Exception {
        for(var existing:List.of(new String[]{"ACCOUNTING_FIRM_VIEW"},new String[]{"ACCOUNTING_FIRM_CREATE","ACCOUNTING_FIRM_VIEW","PLATFORM_USER_MANAGE"},
                new String[]{"ACCOUNTING_FIRM_CREATE","ACCOUNTING_FIRM_VIEW","PLATFORM_USER_MANAGE","UNASSIGNED_COMPANY_ASSIGN","SYNTHETIC_UNKNOWN_PERMISSION"},
                new String[]{"SYNTHETIC_UNKNOWN_PERMISSION"})) {
            cleanup();
            try(var c=connect()) {
                StagingSeed.provision(c,HASH);addPlatform(c,existing);
                var before=platform(c);String credentials=credentials(c),access=accessSnapshot(c);
                assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).as(String.join(",",existing)).isInstanceOf(SQLException.class);
                assertThat(platform(c)).isEqualTo(before);
                assertThat(credentials(c)).isEqualTo(credentials);assertThat(accessSnapshot(c)).isEqualTo(access);
            }
        }
    }
    @Test void platformAdminRefusesDivergentSyntheticIdentity() throws Exception {
        for(var change:List.of("email='changed@finemp.example.invalid'","active=false","active=false,invitation_pending=true")) {
            cleanup();
            try(var c=connect();var s=c.createStatement()) {
                StagingSeed.provision(c,HASH);
                s.executeUpdate("UPDATE user_accounts SET "+change+" WHERE id='"+StagingSeed.id("user")+"'");
                String credentials=credentials(c);
                assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).as(change).isInstanceOf(SQLException.class);
                assertThat(platform(c)).isEmpty();assertThat(credentials(c)).isEqualTo(credentials);
            }
        }
        cleanup();
        // Receipt apontando para outro usuário: identidade divergente.
        try(var c=connect();var s=c.createStatement()) {
            StagingSeed.provision(c,HASH);StagingSeed.provisionIsolationCompany(c);
            s.executeUpdate("UPDATE staging_seed_receipts SET company_id='"+StagingSeed.id("foreign-company")+"' WHERE seed_key='finemp-synthetic-v1'");
            assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).isInstanceOf(SQLException.class);
            assertThat(platform(c)).isEmpty();
        }
    }
    @Test void platformAdminLateFailureLeavesNoPartialPermissionSet() throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            StagingSeed.provision(c,HASH);
            s.execute("CREATE FUNCTION reject_platform() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.permission='UNASSIGNED_COMPANY_ASSIGN' THEN RAISE EXCEPTION 'Synthetic failure'; END IF; RETURN NEW; END; $$");
            s.execute("CREATE TRIGGER reject_platform BEFORE INSERT ON user_account_platform_permissions FOR EACH ROW EXECUTE FUNCTION reject_platform()");
            assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).isInstanceOf(SQLException.class);
            assertThat(platform(c)).isEmpty();
            assertThat(c.getAutoCommit()).isTrue();
            s.execute("DROP TRIGGER reject_platform ON user_account_platform_permissions");
            assertThat(StagingSeed.grantSyntheticPlatformAdmin(c)).isTrue();
            assertThat(platform(c)).containsExactlyElementsOf(PLATFORM);
        }
    }
    @Test void concurrentPlatformAdminCommandsGrantOnce() throws Exception {
        try(var c=connect()){StagingSeed.provision(c,HASH);}
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            Callable<Boolean> action=()->{try(var c=connect()){return StagingSeed.grantSyntheticPlatformAdmin(c);}};
            var first=pool.submit(action);var second=pool.submit(action);
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        try(var c=connect()){assertThat(platform(c)).containsExactlyElementsOf(PLATFORM);}
    }
    @Test void platformAdminRefusesAnOpenCallerTransaction() throws Exception {
        try(var c=connect()) {
            StagingSeed.provision(c,HASH);c.setAutoCommit(false);
            assertThatThrownBy(()->StagingSeed.grantSyntheticPlatformAdmin(c)).isInstanceOf(IllegalArgumentException.class);
            c.rollback();c.setAutoCommit(true);
            assertThat(platform(c)).isEmpty();
        }
    }
}
