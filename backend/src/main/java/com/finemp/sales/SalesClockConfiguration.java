package com.finemp.sales;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SalesClockConfiguration {

    @Bean
    Clock salesClock() {
        return Clock.systemDefaultZone();
    }
}
