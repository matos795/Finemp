package com.finemp.connector.work;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.sync.SyncRunMode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ConnectorWorkResponse(
        UUID attemptId,
        UUID runId,
        int attemptNumber,
        SyncRunMode mode,
        Instant leaseExpiresAt,
        Instant serverTime,
        AccountingReadRequest context,
        Set<Integer> acceptedContractVersions,
        Set<AccountingDataset> acceptedDatasets) {

    public ConnectorWorkResponse {
        acceptedContractVersions = acceptedContractVersions == null
                ? Set.of(1)
                : Set.copyOf(acceptedContractVersions);
        acceptedDatasets = acceptedDatasets == null
                ? Set.of(AccountingDataset.MOVEMENTS)
                : Set.copyOf(acceptedDatasets);
    }

    public ConnectorWorkResponse(
            UUID attemptId,
            UUID runId,
            int attemptNumber,
            SyncRunMode mode,
            Instant leaseExpiresAt,
            Instant serverTime,
            AccountingReadRequest context) {
        this(
                attemptId,
                runId,
                attemptNumber,
                mode,
                leaseExpiresAt,
                serverTime,
                context,
                Set.of(1),
                Set.of(AccountingDataset.MOVEMENTS));
    }

    public boolean acceptsContractVersion(int version) {
        return acceptedContractVersions.contains(version);
    }
}
