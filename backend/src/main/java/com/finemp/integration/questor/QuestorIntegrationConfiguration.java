package com.finemp.integration.questor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "finemp.integration.questor", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QuestorConnectionProperties.class)
public class QuestorIntegrationConfiguration {

    @Bean
    com.finemp.accounting.reading.AccountingSourceReader questorSourceReader(QuestorAccountingReader delegate) {
        return new QuestorSourceReader(delegate);
    }

    @Bean
    QuestorConnectionProvider questorConnectionProvider(
            QuestorConnectionProperties properties) {
        return new QuestorConnectionProvider(properties);
    }

    @Bean
    QuestorAccountingReader questorAccountingReader(
            QuestorConnectionProvider connectionProvider,
            @org.springframework.beans.factory.annotation.Value("${finemp.sync.publication.max-records:25000}") int maxRecords) {
        return new FirebirdQuestorAccountingReader(
                connectionProvider, maxRecords);
    }
}
