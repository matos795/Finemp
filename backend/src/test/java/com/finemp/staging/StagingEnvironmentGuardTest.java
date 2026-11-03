package com.finemp.staging;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class StagingEnvironmentGuardTest {
    private MockEnvironment staging() {
        var environment=new MockEnvironment();
        environment.setActiveProfiles("prod","staging");
        environment.setProperty("PORT","9123");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
    @Test void loadsRealStagingConfigurationAndPreservesSafeDefaults() {
        var environment=staging();
        new StagingEnvironmentGuard().postProcessEnvironment(environment,new SpringApplication());
        assertThat(environment.getProperty("server.port")).isEqualTo("9123");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only",Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.flyway.enabled",Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        for(var flag:new String[]{"catalog","entries","presentation","aging","daily-cash","monthly-cash","operational-dre"})
            assertThat(environment.getProperty("finemp.finance."+flag+".enabled",Boolean.class)).isTrue();
    }
    @Test void unsafeOverridesCannotStartStaging() {
        for(var property:new String[]{"finemp.integration.questor.enabled","finemp.integration.questor.identity.enabled","finemp.connector.enabled","finemp.connector.protocol.enabled","finemp.sync.worker.enabled","finemp.sync.executor.enabled","finemp.sync.control.enabled","finemp.local-user.enabled"}) {
            var env=staging();env.getPropertySources().addFirst(new MapPropertySource("override",Map.of(property,"true")));
            assertThatThrownBy(()->new StagingEnvironmentGuard().postProcessEnvironment(env,new SpringApplication())).isInstanceOf(IllegalStateException.class);
        }
        var env=staging();env.getPropertySources().addFirst(new MapPropertySource("override",Map.of("finemp.integration.questor.password","synthetic-sensitive-value")));
        assertThatThrownBy(()->new StagingEnvironmentGuard().postProcessEnvironment(env,new SpringApplication())).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("synthetic-sensitive-value");
    }
    @Test void invalidProfileCombinationsFailAndExistingProfilesAreUnaffected() {
        for(var profiles:new String[][]{{"staging"},{"prod","local","staging"}}) {
            var env=new MockEnvironment();env.setActiveProfiles(profiles);
            assertThatThrownBy(()->new StagingEnvironmentGuard().postProcessEnvironment(env,new SpringApplication())).isInstanceOf(IllegalStateException.class);
        }
        var local=new MockEnvironment();local.setActiveProfiles("local");local.setProperty("finemp.local-user.enabled","true");
        new StagingEnvironmentGuard().postProcessEnvironment(local,new SpringApplication());
    }
    @Test void guardIsDiscoveredBySpringBootBeforeContextCreation() {
        var application=new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        assertThatThrownBy(()->application.run("--spring.profiles.active=staging", "--spring.main.banner-mode=off"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Staging configuration rejected: profiles");
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    static class EmptyConfiguration {}
}
