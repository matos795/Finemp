package com.finemp.production;

import com.finemp.identity.AccountPasswordPolicy;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * One-shot recovery for the first production administrator before onboarding.
 * Never starts Spring.
 */
public final class ProductionFirstAdminPasswordResetMain {

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println(
                    "FINEMP first-admin password reset: environment-only configuration required.");
            return;
        }

        try {
            if (args.length != 0) {
                throw new IllegalArgumentException();
            }

            var env = System.getenv();
            validateTarget(env);

            String email = ProductionTargetGuard
                    .required(env, "FINEMP_PRODUCTION_ADMIN_EMAIL")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            String newPassword = ProductionTargetGuard
                    .required(env, "FINEMP_PRODUCTION_ADMIN_NEW_PASSWORD");

            if (email.length() > 255
                    || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                    || !AccountPasswordPolicy.isValid(newPassword)) {
                throw new IllegalArgumentException();
            }

            var passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

            try (var connection = DriverManager.getConnection(
                    ProductionTargetGuard.required(env, "SPRING_DATASOURCE_URL"),
                    ProductionTargetGuard.required(env, "SPRING_DATASOURCE_USERNAME"),
                    ProductionTargetGuard.required(env, "SPRING_DATASOURCE_PASSWORD"))) {

                boolean changed = ProductionFirstAdminPasswordReset.reset(
                        connection,
                        email,
                        newPassword,
                        passwordEncoder);

                System.out.println(changed
                        ? "FINEMP first administrator password reset."
                        : "FINEMP first administrator password already matches; unchanged.");
            }
        } catch (Exception failure) {
            System.err.println(
                    "First-admin password reset refused or failed; no configuration values logged.");
            System.exit(2);
        }
    }

    static void validateTarget(Map<String, String> env) {
        ProductionTargetGuard.validate(
                env,
                "FINEMP_PRODUCTION_ADMIN_PASSWORD_RESET_CONFIRM",
                "RESET_FINEMP_FIRST_ADMIN_PASSWORD");
    }
}
