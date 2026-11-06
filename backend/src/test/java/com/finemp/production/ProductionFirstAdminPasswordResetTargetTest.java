package com.finemp.production;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionFirstAdminPasswordResetTargetTest {

    private Map<String, String> valid() {
        String project = UUID.randomUUID().toString();
        var env = new HashMap<String, String>();
        env.put("SPRING_PROFILES_ACTIVE", "prod");
        env.put(
                "FINEMP_PRODUCTION_ADMIN_PASSWORD_RESET_CONFIRM",
                "RESET_FINEMP_FIRST_ADMIN_PASSWORD");
        env.put("RAILWAY_PROJECT_NAME", "finemp-production");
        env.put("RAILWAY_ENVIRONMENT_NAME", "production");
        env.put("FINEMP_PRODUCTION_PROJECT_ID", project);
        env.put("RAILWAY_PROJECT_ID", project);
        env.put(
                "SPRING_DATASOURCE_URL",
                "jdbc:postgresql://postgres.railway.internal:5432/railway");
        return env;
    }

    @Test
    void acceptsOnlyTheExplicitPasswordResetTarget() {
        ProductionFirstAdminPasswordResetMain.validateTarget(valid());

        for (String key : valid().keySet()) {
            var env = valid();
            env.remove(key);
            assertThatThrownBy(() -> ProductionFirstAdminPasswordResetMain.validateTarget(env))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void doesNotAcceptTheBootstrapConfirmationForPasswordReset() {
        var env = valid();
        env.put(
                "FINEMP_PRODUCTION_ADMIN_PASSWORD_RESET_CONFIRM",
                "BOOTSTRAP_FINEMP_PRODUCTION");

        assertThatThrownBy(() -> ProductionFirstAdminPasswordResetMain.validateTarget(env))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
