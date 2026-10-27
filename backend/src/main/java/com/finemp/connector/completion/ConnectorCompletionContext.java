package com.finemp.connector.completion;

import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.SyncControlTransactionGuard;
import com.finemp.accounting.sync.execution.*;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.connector.*;
import com.finemp.connector.work.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
public class ConnectorCompletionContext {
    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;
    private final SyncAttemptRepository attempts;
    private final ConnectorCredentialVerifier credentials;
    private final SyncControlTransactionGuard transactions;
    private final Clock clock;
    public ConnectorCompletionContext(AccountingSourceRepository sources, SyncRunRepository runs, SyncAttemptRepository attempts,
            ConnectorCredentialVerifier credentials, SyncControlTransactionGuard transactions, Clock clock) {
        this.sources = sources; this.runs = runs; this.attempts = attempts; this.credentials = credentials;
        this.transactions = transactions; this.clock = clock;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimedSyncRun currentClaim(ConnectorIdentity identity, UUID attemptId) {
        var locked = lock(identity, attemptId); var run = locked.run(); var attempt = locked.attempt(); var source = locked.source();
        if (run.getStatus() != SyncRunStatus.RUNNING || run.getVersion() != attempt.getFenceVersion() || !attempt.isLive(clock.instant()))
            throw new ConnectorWorkNotCurrentException();
        return new ClaimedSyncRun(run.getId(), attempt.getFenceVersion(), run.getMode(), new AccountingReadRequest(source.getId(),
                source.getCompany().getId(), source.getType(), source.getExternalReference(), source.getCompany().getCnpj(),
                run.getStartDate(), run.getEndDate()), attempt.getAgent());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConnectorWorkResult result(ConnectorIdentity identity, UUID attemptId) {
        var locked = lock(identity, attemptId); var run = locked.run(); var attempt = locked.attempt();
        boolean succeededHere = attempt.getStatus() == SyncAttemptStatus.SUCCEEDED && run.getStatus() == SyncRunStatus.SUCCEEDED;
        return new ConnectorWorkResult(attemptId, run.getId(), attempt.getAttemptNumber(), run.getAttemptNumber() == attempt.getAttemptNumber(),
                attempt.getStatus(), run.getStatus(), attempt.getLeaseExpiresAt(), attempt.getNextAttemptAt(), attempt.getFailureCode(),
                run.getFailureCode(), succeededHere ? run.getReadCount() : null, succeededHere ? run.getImportedCount() : null,
                succeededHere ? run.getSkippedCount() : null);
    }
    private LockedWork lock(ConnectorIdentity identity, UUID attemptId) {
        transactions.requireReadCommitted();
        var source = sources.findLockedForSyncById(identity.accountingSourceId()).orElseThrow(ConnectorCredentialUnavailableException::new);
        if (!credentials.isCurrent(identity) || source.getConnectionMode() != AccountingConnectionMode.CONNECTOR) throw new ConnectorCredentialUnavailableException();
        var expected = new SyncClaimAgent(identity.credentialId(), identity.version());
        var attempt = attempts.findById(attemptId).filter(value -> value.getAgent().equals(expected))
                .orElseThrow(() -> new SaasResourceNotFoundException("Work"));
        var run = runs.findByIdAndAccountingSourceId(attempt.getSyncRunId(), source.getId()).orElseThrow(() -> new SaasResourceNotFoundException("Work"));
        return new LockedWork(source, run, attempt);
    }
    private record LockedWork(AccountingSource source, SyncRun run, SyncAttempt attempt) { }
}
