package com.finemp.accounting.sync.control;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "finemp.sync.control", name = "enabled", havingValue = "true")
public class SyncControlConfiguration {
    @Bean("syncControlClock") Clock syncControlClock() { return Clock.systemUTC(); }
}

