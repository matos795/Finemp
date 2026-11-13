package com.finemp.staging;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** #334: cada modo exige a própria confirmação; o alvo staging continua obrigatório para todos. */
class StagingProvisioningModeTest {
    static final String PLATFORM_CONFIRM="GRANT_SYNTHETIC_STAGING_PLATFORM_ADMIN";
    static final String SYSTEM_CONFIRM="GRANT_SYNTHETIC_STAGING_SYSTEM_ADMIN";
    Map<String,String> target(){String project=UUID.randomUUID().toString();return new HashMap<>(Map.of("SPRING_PROFILES_ACTIVE","prod,staging",
            "RAILWAY_PROJECT_NAME","finemp-staging","RAILWAY_ENVIRONMENT_NAME","staging","FINEMP_STAGING_PROJECT_ID",project,"RAILWAY_PROJECT_ID",project,
            "SPRING_DATASOURCE_URL","jdbc:postgresql://postgres.railway.internal:5432/railway"));}
    Map<String,String> platform(){var env=target();env.put("FINEMP_STAGING_PLATFORM_ADMIN_CONFIRM",PLATFORM_CONFIRM);return env;}
    Map<String,String> system(){var env=target();env.put("FINEMP_STAGING_SYSTEM_ADMIN_CONFIRM",SYSTEM_CONFIRM);return env;}
    Map<String,String> seed(){var env=target();env.put("FINEMP_STAGING_SEED_CONFIRM","PROVISION_SYNTHETIC_STAGING");return env;}

    @Test void parsesOnlySingleKnownModes() {
        assertThat(StagingProvisioningMain.Mode.parse(new String[0])).isEqualTo(StagingProvisioningMain.Mode.PROVISION);
        assertThat(StagingProvisioningMain.Mode.parse(new String[]{"--isolation-company"})).isEqualTo(StagingProvisioningMain.Mode.ISOLATION_COMPANY);
        assertThat(StagingProvisioningMain.Mode.parse(new String[]{"--reset-password"})).isEqualTo(StagingProvisioningMain.Mode.RESET_PASSWORD);
        assertThat(StagingProvisioningMain.Mode.parse(new String[]{"--platform-admin"})).isEqualTo(StagingProvisioningMain.Mode.PLATFORM_ADMIN);
        assertThat(StagingProvisioningMain.Mode.parse(new String[]{"--system-admin"})).isEqualTo(StagingProvisioningMain.Mode.SYSTEM_ADMIN);
        for(var args:List.of(new String[]{"--platform-admin","--reset-password"},new String[]{"--platform-admin=true"},new String[]{"--PLATFORM-ADMIN"},new String[]{"--admin"},new String[]{"--platform-admin","--isolation-company"}))
            assertThatThrownBy(()->StagingProvisioningMain.Mode.parse(args)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void platformAdminRequiresItsOwnConfirmationAndNotTheSeedOptIn() {
        StagingProvisioningMain.validateTarget(platform(),StagingProvisioningMain.Mode.PLATFORM_ADMIN);
        assertThatThrownBy(()->StagingProvisioningMain.validateTarget(seed(),StagingProvisioningMain.Mode.PLATFORM_ADMIN)).isInstanceOf(IllegalArgumentException.class);
        for(var value:List.of("grant_synthetic_staging_platform_admin","PROVISION_SYNTHETIC_STAGING","true","GRANT_SYNTHETIC_STAGING_PLATFORM_ADMIN ")) {
            var env=target();env.put("FINEMP_STAGING_PLATFORM_ADMIN_CONFIRM",value);
            assertThatThrownBy(()->StagingProvisioningMain.validateTarget(env,StagingProvisioningMain.Mode.PLATFORM_ADMIN)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void systemAdminRequiresItsOwnConfirmationAndDoesNotReusePlatformOrSeedOptIns() {
        StagingProvisioningMain.validateTarget(system(),StagingProvisioningMain.Mode.SYSTEM_ADMIN);
        assertThatThrownBy(()->StagingProvisioningMain.validateTarget(platform(),StagingProvisioningMain.Mode.SYSTEM_ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->StagingProvisioningMain.validateTarget(seed(),StagingProvisioningMain.Mode.SYSTEM_ADMIN)).isInstanceOf(IllegalArgumentException.class);
        for(var value:List.of("grant_synthetic_staging_system_admin","GRANT_SYNTHETIC_STAGING_PLATFORM_ADMIN","true","GRANT_SYNTHETIC_STAGING_SYSTEM_ADMIN ")) {
            var env=target();env.put("FINEMP_STAGING_SYSTEM_ADMIN_CONFIRM",value);
            assertThatThrownBy(()->StagingProvisioningMain.validateTarget(env,StagingProvisioningMain.Mode.SYSTEM_ADMIN)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void elevationConfirmationNeverAuthorizesFixtureOrPasswordModes() {
        for(var mode:List.of(StagingProvisioningMain.Mode.PROVISION,StagingProvisioningMain.Mode.ISOLATION_COMPANY,StagingProvisioningMain.Mode.RESET_PASSWORD)) {
            assertThatThrownBy(()->StagingProvisioningMain.validateTarget(platform(),mode)).isInstanceOf(IllegalArgumentException.class);
            StagingProvisioningMain.validateTarget(seed(),mode);
        }
    }
    @Test void platformAdminKeepsEveryExistingTargetGuard() {
        for(var key:platform().keySet()){var env=platform();env.remove(key);
            assertThatThrownBy(()->StagingProvisioningMain.validateTarget(env,StagingProvisioningMain.Mode.PLATFORM_ADMIN)).as(key).isInstanceOf(IllegalArgumentException.class);}
        for(var change:List.of(Map.entry("SPRING_PROFILES_ACTIVE","prod"),Map.entry("SPRING_PROFILES_ACTIVE","prod,staging,local"),Map.entry("SPRING_PROFILES_ACTIVE","local"),
                Map.entry("RAILWAY_PROJECT_NAME","finemp-production"),Map.entry("RAILWAY_ENVIRONMENT_NAME","production"),Map.entry("RAILWAY_PROJECT_ID",UUID.randomUUID().toString()),
                Map.entry("SPRING_DATASOURCE_URL","jdbc:postgresql://public.example:5432/db"),Map.entry("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/finemp"),
                Map.entry("SPRING_DATASOURCE_URL","jdbc:postgresql://postgres.railway.internal:5432/db?socketFactory=custom"))) {
            var env=platform();env.put(change.getKey(),change.getValue());
            assertThatThrownBy(()->StagingProvisioningMain.validateTarget(env,StagingProvisioningMain.Mode.PLATFORM_ADMIN)).as(change.toString()).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void platformAdminNeverNeedsOrReadsThePassword() {
        assertThat(StagingProvisioningMain.Mode.PLATFORM_ADMIN.needsPassword()).isFalse();
        assertThat(StagingProvisioningMain.Mode.SYSTEM_ADMIN.needsPassword()).isFalse();
        assertThat(StagingProvisioningMain.Mode.ISOLATION_COMPANY.needsPassword()).isFalse();
        assertThat(StagingProvisioningMain.Mode.PROVISION.needsPassword()).isTrue();
        assertThat(StagingProvisioningMain.Mode.RESET_PASSWORD.needsPassword()).isTrue();
    }
    @Test void refusalMessagesNeverEchoConfigurationValues() {
        var env=platform();env.put("SPRING_DATASOURCE_URL","jdbc:postgresql://secret-host.example:5432/secretdb");
        assertThatThrownBy(()->StagingProvisioningMain.validateTarget(env,StagingProvisioningMain.Mode.PLATFORM_ADMIN))
                .hasMessageNotContaining("secret-host").hasMessageNotContaining("secretdb").hasMessageNotContaining(PLATFORM_CONFIRM);
    }
}
