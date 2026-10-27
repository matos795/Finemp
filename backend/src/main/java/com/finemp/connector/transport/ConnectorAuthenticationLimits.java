package com.finemp.connector.transport;

import com.finemp.identity.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
public class ConnectorAuthenticationLimits {
    private final FixedWindowAttemptLimiter limiter;
    private final int addressLimit, credentialLimit;
    public ConnectorAuthenticationLimits(Clock clock, @Value("${finemp.connector.protocol.rate-limit.capacity:10000}") int capacity,
            @Value("${finemp.connector.protocol.rate-limit.address-per-minute:600}") int addressLimit,
            @Value("${finemp.connector.protocol.rate-limit.credential-per-minute:120}") int credentialLimit) {
        if (capacity < 1 || capacity > 100000 || addressLimit < 1 || addressLimit > 100000 || credentialLimit < 1 || credentialLimit > 10000)
            throw new IllegalArgumentException("Invalid connector authentication limits.");
        limiter = new FixedWindowAttemptLimiter(clock, capacity); this.addressLimit = addressLimit; this.credentialLimit = credentialLimit;
    }
    public boolean allow(String address, String raw) {
        return limiter.tryAcquire("address:" + OpaqueAccountToken.hash(address == null ? "unknown" : address), addressLimit, Duration.ofMinutes(1))
                && limiter.tryAcquire("credential:" + OpaqueAccountToken.hash(raw), credentialLimit, Duration.ofMinutes(1));
    }
}
