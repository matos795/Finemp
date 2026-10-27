package com.finemp.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import com.finemp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "finemp.sync.control.enabled=false")
@Import(TestcontainersConfiguration.class)
class SyncControlDisabledIntegrationTest {
    @Autowired private ApplicationContext context;
    @Test void shouldNotPublishControlHandlersUntilExplicitlyEnabled() {
        assertThat(context.containsBean("syncControlController")).isFalse();
        assertThat(context.containsBean("syncControlService")).isFalse();
        assertThat(context.containsBean("syncControlTransactionGuard")).isFalse();
    }
}

