package com.finemp.accounting.sync.execution;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.finemp.accounting.reading.*;
import com.finemp.integration.questor.QuestorAccountingImportController;
import com.finemp.integration.questor.QuestorAccountingImportService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class SyncExecutorConfigurationTest {
    @Test void shouldFailClosedWithoutAnExternalIdentityReader() {
        try (var context = context(true)) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                    .hasStackTraceContaining("AccountingCompanyIdentityReader");
        }
    }
    @Test void shouldFailClosedWithoutAnAtomicPublisher() {
        try (var context = context(true)) {
            context.registerBean(AccountingCompanyIdentityReader.class, () -> mock(AccountingCompanyIdentityReader.class));
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                    .hasStackTraceContaining("SyncPublicationPort");
        }
    }
    @Test void shouldKeepExecutorUnpublishedByDefault() {
        try (var context = context(false)) {
            context.refresh();
            assertThat(context.getBeansOfType(SyncRunExecutor.class)).isEmpty();
        }
    }
    @Test void shouldDisableTheLegacyWritePathWhenControlledExecutionIsEnabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic",
                    Map.of("finemp.integration.questor.enabled", "true", "finemp.sync.executor.enabled", "true")));
            context.register(QuestorAccountingImportController.class, QuestorAccountingImportService.class);
            context.refresh();
            assertThat(context.getBeansOfType(QuestorAccountingImportController.class)).isEmpty();
            assertThat(context.getBeansOfType(QuestorAccountingImportService.class)).isEmpty();
        }
    }
    private AnnotationConfigApplicationContext context(boolean enabled) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic",
                Map.of("finemp.sync.executor.enabled", String.valueOf(enabled))));
        context.registerBean(SyncExecutionLifecycle.class, () -> mock(SyncExecutionLifecycle.class));
        context.registerBean(AccountingSourceReaderRegistry.class, () -> mock(AccountingSourceReaderRegistry.class));
        context.register(SyncRunExecutor.class);
        return context;
    }
    @Test void shouldNeverPublishTheUnverifiedLegacyImportInProductionProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic",
                    Map.of("finemp.integration.questor.enabled", "true", "finemp.sync.executor.enabled", "false")));
            context.register(QuestorAccountingImportController.class, QuestorAccountingImportService.class);
            context.refresh();
            assertThat(context.getBeansOfType(QuestorAccountingImportController.class)).isEmpty();
            assertThat(context.getBeansOfType(QuestorAccountingImportService.class)).isEmpty();
        }
    }
}

