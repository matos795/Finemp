package com.finemp.staging;

import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Reject unsafe staging overrides before database initialization or integration beans. */
public final class StagingEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var profiles=Set.of(environment.getActiveProfiles());
        if (!profiles.contains("staging")) return;
        if (!profiles.contains("prod") || profiles.contains("local")) fail("profiles");
        for (var property : new String[]{"finemp.local-user.enabled", "finemp.integration.questor.enabled",
                "finemp.integration.questor.identity.enabled", "finemp.connector.enabled", "finemp.connector.protocol.enabled",
                "finemp.sync.worker.enabled", "finemp.sync.executor.enabled", "finemp.sync.control.enabled"}) {
            if (environment.getProperty(property,Boolean.class,false)) fail(property);
        }
        for (var property : new String[]{"finemp.integration.questor.jdbc-url", "finemp.integration.questor.username", "finemp.integration.questor.password"}) {
            if (!environment.getProperty(property,"").isBlank()) fail(property);
        }
        for (var property : new String[]{"server.servlet.session.cookie.secure", "server.servlet.session.cookie.http-only", "spring.flyway.enabled"}) {
            if (!environment.getProperty(property,Boolean.class,false)) fail(property);
        }
        if (!"lax".equalsIgnoreCase(environment.getProperty("server.servlet.session.cookie.same-site"))) fail("cookie.same-site");
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) fail("ddl-auto");
        if (!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy"))) fail("forward-headers-strategy");
    }
    private static void fail(String property) {
        throw new IllegalStateException("Staging configuration rejected: " + property);
    }
}
