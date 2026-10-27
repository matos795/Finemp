package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import com.finemp.accounting.reading.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class QuestorIdentityConfigurationTest {
    @Test void shouldRequireExplicitMappingWithoutOpeningAnyDatabaseAtStartup() {
        try (var context = context(Map.of("finemp.integration.questor.identity.enabled", "true"))) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("Invalid Questor identity mapping");
        }
    }
    @Test void shouldWireOneVerifiedCanonicalReaderAndSnapshotPortOnlyWhenMappingIsEnabled() {
        try (var context = context(Map.of("finemp.integration.questor.identity.enabled", "true",
                "finemp.integration.questor.identity.table", "SYN_COMPANIES",
                "finemp.integration.questor.identity.code-column", "COMPANY_CODE",
                "finemp.integration.questor.identity.cnpj-column", "TAX_ID"))) {
            context.refresh();
            assertThat(context.getBeansOfType(AccountingSourceReader.class)).hasSize(1);
            assertThat(context.getBean(AccountingSourceReader.class)).isInstanceOf(VerifiedQuestorSnapshotReader.class);
            assertThat(context.getBean(AccountingSourceSnapshotReader.class)).isSameAs(context.getBean(AccountingSourceReader.class));
            assertThat(context.getBeansOfType(AccountingCompanyIdentityReader.class)).hasSize(1);
        }
    }
    @Test void shouldKeepIdentityUnconfiguredByDefaultInsteadOfEchoingExpectations() {
        try (var context = context(Map.of())) {
            context.refresh();
            assertThat(context.getBeansOfType(AccountingCompanyIdentityReader.class)).isEmpty();
            assertThat(context.getBeansOfType(AccountingSourceSnapshotReader.class)).isEmpty();
            assertThat(context.getBean(AccountingSourceReader.class)).isInstanceOf(QuestorSourceReader.class);
        }
    }
    private AnnotationConfigApplicationContext context(Map<String, Object> additions) {
        var context = new AnnotationConfigApplicationContext();
        var properties = new java.util.HashMap<String, Object>(additions); properties.put("finemp.integration.questor.enabled", "true");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic", properties));
        context.register(QuestorIntegrationConfiguration.class); return context;
    }
}
