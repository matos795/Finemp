package com.finemp.production;

import com.finemp.identity.AccountPasswordPolicy;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** One-shot production bootstrap. Never starts Spring or the HTTP server. */
public final class ProductionAdminBootstrapMain {
    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("FINEMP production admin bootstrap: environment-only configuration required; no secrets as arguments.");
            return;
        }
        try {
            if (args.length != 0) throw new IllegalArgumentException();
            var env = System.getenv();
            validateTarget(env);
            String name = required(env, "FINEMP_PRODUCTION_ADMIN_NAME").trim();
            String email = required(env, "FINEMP_PRODUCTION_ADMIN_EMAIL").trim().toLowerCase(Locale.ROOT);
            String password = required(env, "FINEMP_PRODUCTION_ADMIN_PASSWORD");
            if (name.length() < 2 || name.length() > 150) throw new IllegalArgumentException();
            if (email.length() > 255 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException();
            if (!AccountPasswordPolicy.isValid(password)) throw new IllegalArgumentException();
            String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password);
            try (var connection = DriverManager.getConnection(
                    required(env, "SPRING_DATASOURCE_URL"),
                    required(env, "SPRING_DATASOURCE_USERNAME"),
                    required(env, "SPRING_DATASOURCE_PASSWORD"))) {
                boolean created = ProductionAdminBootstrap.provision(connection, name, email, hash);
                System.out.println(created
                        ? "FINEMP production platform administrator created."
                        : "FINEMP production platform administrator already exists; unchanged.");
            }
        } catch (Exception failure) {
            System.err.println("Production admin bootstrap refused or failed; no configuration values logged.");
            System.exit(2);
        }
    }

    static void validateTarget(Map<String,String> env) {
        var profiles = Arrays.stream(required(env, "SPRING_PROFILES_ACTIVE").split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        if (!profiles.equals(Set.of("prod"))) throw new IllegalArgumentException("Production profile required");
        if (!"BOOTSTRAP_FINEMP_PRODUCTION".equals(env.get("FINEMP_PRODUCTION_BOOTSTRAP_CONFIRM"))
                || !"finemp-production".equals(env.get("RAILWAY_PROJECT_NAME"))
                || !"production".equals(env.get("RAILWAY_ENVIRONMENT_NAME"))) {
            throw new IllegalArgumentException("Unconfirmed production target");
        }
        UUID expected = UUID.fromString(required(env, "FINEMP_PRODUCTION_PROJECT_ID"));
        if (!expected.equals(UUID.fromString(required(env, "RAILWAY_PROJECT_ID")))) {
            throw new IllegalArgumentException("Production project mismatch");
        }
        if (!required(env, "SPRING_DATASOURCE_URL")
                .matches("jdbc:postgresql://[a-zA-Z0-9-]+\\.railway\\.internal:[0-9]{1,5}/[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Private production datasource required");
        }
    }

    private static String required(Map<String,String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing production bootstrap configuration");
        return value;
    }
}
