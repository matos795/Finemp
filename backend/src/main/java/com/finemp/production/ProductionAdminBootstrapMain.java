package com.finemp.production;

import com.finemp.identity.AccountPasswordPolicy;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** One-shot bootstrap for the first platform administrator. Never starts Spring. */
public final class ProductionAdminBootstrapMain {
    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("FINEMP first-admin bootstrap: environment-only configuration required.");
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
                System.out.println(created ? "FINEMP first platform administrator created." : "FINEMP first platform administrator already exists; unchanged.");
            }
        } catch (Exception failure) {
            System.err.println("First-admin bootstrap refused or failed; no configuration values logged.");
            System.exit(2);
        }
    }

    static void validateTarget(Map<String,String> env) {
        ProductionTargetGuard.validate(
                env,
                "FINEMP_PRODUCTION_BOOTSTRAP_CONFIRM",
                "BOOTSTRAP_FINEMP_PRODUCTION");
    }

    private static String required(Map<String,String> env, String key) {
        return ProductionTargetGuard.required(env, key);
    }
}
