package com.finemp.production;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ProductionTargetGuard {

    private ProductionTargetGuard() {
    }

    static void validate(
            Map<String, String> env,
            String confirmationKey,
            String expectedConfirmation) {

        var profiles = Arrays.stream(required(env, "SPRING_PROFILES_ACTIVE").split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());

        if (!profiles.equals(Set.of("prod"))) {
            throw new IllegalArgumentException("Production profile required");
        }

        if (!expectedConfirmation.equals(env.get(confirmationKey))
                || !"finemp-production".equals(env.get("RAILWAY_PROJECT_NAME"))
                || !"production".equals(env.get("RAILWAY_ENVIRONMENT_NAME"))) {
            throw new IllegalArgumentException("Unconfirmed production target");
        }

        UUID expectedProjectId = UUID.fromString(required(env, "FINEMP_PRODUCTION_PROJECT_ID"));
        UUID railwayProjectId = UUID.fromString(required(env, "RAILWAY_PROJECT_ID"));

        if (!expectedProjectId.equals(railwayProjectId)) {
            throw new IllegalArgumentException("Production project mismatch");
        }

        if (!required(env, "SPRING_DATASOURCE_URL")
                .matches("jdbc:postgresql://[a-zA-Z0-9-]+\\.railway\\.internal:[0-9]{1,5}/[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Private production datasource required");
        }
    }

    static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing production configuration");
        }
        return value;
    }
}
