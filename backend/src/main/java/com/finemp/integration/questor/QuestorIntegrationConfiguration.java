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
    @ConditionalOnProperty(prefix = "finemp.integration.questor.identity", name = "enabled", havingValue = "false", matchIfMissing = true)
    com.finemp.accounting.reading.AccountingSourceReader questorSourceReader(QuestorAccountingReader delegate) {
        return new QuestorSourceReader(delegate);
    }

    @Bean
    QuestorConnectionProvider questorConnectionProvider(
            QuestorConnectionProperties properties) {
        return new QuestorConnectionProvider(properties);
    }

    @Bean
    FirebirdQuestorAccountingReader questorAccountingReader(
            QuestorConnectionProvider connectionProvider,
            @org.springframework.beans.factory.annotation.Value("${finemp.sync.publication.max-records:25000}") int maxRecords,
            @org.springframework.beans.factory.annotation.Value("${finemp.integration.questor.query-timeout-seconds:30}") int timeoutSeconds) {
        return new FirebirdQuestorAccountingReader(
                connectionProvider, maxRecords, timeoutSeconds);
    }
    @Bean
    @ConditionalOnProperty(prefix = "finemp.integration.questor.identity", name = "enabled", havingValue = "true")
    FirebirdQuestorCompanyIdentityReader questorCompanyIdentityReader(QuestorConnectionProvider provider,
            @org.springframework.beans.factory.annotation.Value("${finemp.integration.questor.identity.table:}") String table,
            @org.springframework.beans.factory.annotation.Value("${finemp.integration.questor.identity.code-column:}") String codeColumn,
            @org.springframework.beans.factory.annotation.Value("${finemp.integration.questor.identity.cnpj-column:}") String cnpjColumn,
            @org.springframework.beans.factory.annotation.Value("${finemp.integration.questor.query-timeout-seconds:30}") int timeoutSeconds) {
        return new FirebirdQuestorCompanyIdentityReader(provider, new QuestorIdentityMapping(table, codeColumn, cnpjColumn), timeoutSeconds);
    }
    @Bean
    @ConditionalOnProperty(prefix = "finemp.integration.questor.identity", name = "enabled", havingValue = "true")
    VerifiedQuestorSnapshotReader verifiedQuestorSnapshotReader(QuestorConnectionProvider provider,
            FirebirdQuestorCompanyIdentityReader identities, FirebirdQuestorAccountingReader movements) {
        return new VerifiedQuestorSnapshotReader(provider, identities, movements);
    }
}
