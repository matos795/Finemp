package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class QuestorConnectionPropertiesTest {
    @Test
    void shouldNeverExposeConnectionConfigurationThroughRecordToString() {
        var properties = new QuestorConnectionProperties("synthetic-private-address", "synthetic-user", "synthetic-secret");
        assertThat(properties.toString()).isEqualTo("QuestorConnectionProperties[redacted]")
                .doesNotContain("synthetic-private-address", "synthetic-user", "synthetic-secret");
    }
}
