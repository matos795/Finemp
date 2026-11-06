package com.finemp.production;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductionAdminBootstrapTargetTest {
    private Map<String,String> valid() {
        String project = UUID.randomUUID().toString();
        var env = new HashMap<String,String>();
        env.put("SPRING_PROFILES_ACTIVE","prod");
        env.put("FINEMP_PRODUCTION_BOOTSTRAP_CONFIRM","BOOTSTRAP_FINEMP_PRODUCTION");
        env.put("RAILWAY_PROJECT_NAME","finemp-production");
        env.put("RAILWAY_ENVIRONMENT_NAME","production");
        env.put("FINEMP_PRODUCTION_PROJECT_ID",project);
        env.put("RAILWAY_PROJECT_ID",project);
        env.put("SPRING_DATASOURCE_URL","jdbc:postgresql://postgres.railway.internal:5432/railway");
        return env;
    }

    @Test void acceptsOnlyExplicitProductionTarget() {
        ProductionAdminBootstrapMain.validateTarget(valid());
        for (String key : valid().keySet()) {
            var env=valid();
            env.remove(key);
            assertThatThrownBy(() -> ProductionAdminBootstrapMain.validateTarget(env))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void refusesStagingLocalWrongProjectAndPublicDatabase() {
        for (var change : Map.of(
                "SPRING_PROFILES_ACTIVE","prod,staging",
                "RAILWAY_PROJECT_NAME","finemp-staging",
                "RAILWAY_ENVIRONMENT_NAME","staging",
                "SPRING_DATASOURCE_URL","jdbc:postgresql://public.example:5432/railway").entrySet()) {
            var env=valid();
            env.put(change.getKey(), change.getValue());
            assertThatThrownBy(() -> ProductionAdminBootstrapMain.validateTarget(env))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var env=valid();
        env.put("RAILWAY_PROJECT_ID",UUID.randomUUID().toString());
        assertThatThrownBy(() -> ProductionAdminBootstrapMain.validateTarget(env))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
