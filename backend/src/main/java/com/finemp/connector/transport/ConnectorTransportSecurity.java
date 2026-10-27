package com.finemp.connector.transport;

import com.finemp.connector.ConnectorCredentialVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
public class ConnectorTransportSecurity {
    @Bean @Order(1)
    SecurityFilterChain connectorMachineChain(HttpSecurity http, ConnectorCredentialVerifier verifier, ConnectorAuthenticationLimits limits,
            @Value("${finemp.connector.protocol.max-batch-bytes:8388608}") int batchBytes,
            @Value("${finemp.connector.protocol.max-metadata-bytes:2048}") int metadataBytes,
            @Value("${finemp.connector.protocol.max-concurrent-requests:2}") int concurrency) throws Exception {
        return http.securityMatcher("/api/connector/v1/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(access -> access.anyRequest().hasAuthority("CONNECTOR_SOURCE"))
                .addFilterBefore(new ConnectorAuthenticationFilter(verifier, limits), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new ConnectorPayloadLimitFilter(batchBytes, metadataBytes, concurrency), ConnectorAuthenticationFilter.class)
                .build();
    }
}
