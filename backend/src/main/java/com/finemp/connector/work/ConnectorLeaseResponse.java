package com.finemp.connector.work;

import java.time.Instant;
import java.util.UUID;

public record ConnectorLeaseResponse(UUID attemptId, Instant leaseExpiresAt, Instant serverTime) { }
