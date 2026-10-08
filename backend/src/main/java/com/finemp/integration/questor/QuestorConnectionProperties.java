package com.finemp.integration.questor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finemp.integration.questor")
public record QuestorConnectionProperties(
        String jdbcUrl,
        String username,
        String password
) {
}