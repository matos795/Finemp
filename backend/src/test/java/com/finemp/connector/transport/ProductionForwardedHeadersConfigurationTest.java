package com.finemp.connector.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class ProductionForwardedHeadersConfigurationTest {

    @Test
    void shouldHonorForwardedHttpsBehindTheProductionReverseProxy() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));

        var properties = yaml.getObject();

        assertThat(properties)
                .isNotNull()
                .containsEntry("server.forward-headers-strategy", "framework");
    }
}
