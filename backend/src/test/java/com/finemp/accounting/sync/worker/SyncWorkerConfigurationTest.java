package com.finemp.accounting.sync.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.finemp.accounting.sync.execution.*;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

class SyncWorkerConfigurationTest {
    @Test void shouldNotCreateAnyBackgroundInfrastructureByDefault() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SyncWorkerConfiguration.class); context.refresh();
            assertThat(context.getBeansOfType(BoundedSyncWorker.class)).isEmpty();
            assertThat(context.getBeansOfType(SyncWorkerPoller.class)).isEmpty();
        }
    }
    @Test void shouldFailClosedWhenWorkerIsEnabledWithoutControlledExecutor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic", Map.of("finemp.sync.worker.enabled", "true")));
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(SyncExecutionLifecycle.class, () -> mock(SyncExecutionLifecycle.class));
            context.registerBean(SyncRetryPolicy.class, () -> new SyncRetryPolicy(3, 300, 30, 300));
            context.register(SyncWorkerConfiguration.class);
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                    .hasStackTraceContaining("SyncRunExecutor");
        }
    }
}
