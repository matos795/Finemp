package com.finemp.connector.work;

import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.sync.SyncRunMode;
import java.time.Instant;
import java.util.UUID;

public record ConnectorWorkResponse(UUID attemptId, UUID runId, int attemptNumber, SyncRunMode mode, Instant leaseExpiresAt, AccountingReadRequest context) { }
