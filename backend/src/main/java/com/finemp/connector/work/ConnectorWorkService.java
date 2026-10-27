package com.finemp.connector.work;

import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.SyncControlTransactionGuard;
import com.finemp.accounting.sync.execution.*;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.connector.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
public class ConnectorWorkService {
    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;
    private final SyncAttemptRepository attempts;
    private final SyncExecutionLifecycle lifecycle;
    private final ConnectorCredentialVerifier credentials;
    private final SyncControlTransactionGuard transactions;
    public ConnectorWorkService(AccountingSourceRepository sources, SyncRunRepository runs, SyncAttemptRepository attempts,
            SyncExecutionLifecycle lifecycle, ConnectorCredentialVerifier credentials, SyncControlTransactionGuard transactions) {
        this.sources = sources; this.runs = runs; this.attempts = attempts; this.lifecycle = lifecycle;
        this.credentials = credentials; this.transactions = transactions;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ConnectorWorkResponse> claim(ConnectorIdentity identity) {
        transactions.requireReadCommitted(); lockAndAuthorize(identity);
        var pending = runs.findFirstByAccountingSourceIdAndStatusInOrderByRequestedAtAscIdAsc(identity.accountingSourceId(),
                Set.of(SyncRunStatus.REQUESTED, SyncRunStatus.RUNNING));
        if (pending.isEmpty()) return Optional.empty();
        var claim = lifecycle.claimConnector(pending.get().getId(), agent(identity));
        if (claim.isEmpty()) return Optional.empty();
        var attempt = attempts.findBySyncRunIdAndFenceVersion(claim.get().runId(), claim.get().version()).orElseThrow(ConnectorWorkNotCurrentException::new);
        return Optional.of(new ConnectorWorkResponse(attempt.getId(), claim.get().runId(), attempt.getAttemptNumber(),
                claim.get().mode(), attempt.getLeaseExpiresAt(), claim.get().request()));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConnectorLeaseResponse renew(ConnectorIdentity identity, UUID attemptId) {
        transactions.requireReadCommitted();
        var source = lockAndAuthorize(identity);
        var attempt = requireAttempt(identity, attemptId);
        var run = runs.findByIdAndAccountingSourceId(attempt.getSyncRunId(), source.getId()).orElseThrow(() -> new SaasResourceNotFoundException("Work"));
        var request = new AccountingReadRequest(source.getId(), source.getCompany().getId(), source.getType(),
                source.getExternalReference(), source.getCompany().getCnpj(), run.getStartDate(), run.getEndDate());
        if (!lifecycle.renewLease(new ClaimedSyncRun(run.getId(), attempt.getFenceVersion(), run.getMode(), request, attempt.getAgent())))
            throw new ConnectorWorkNotCurrentException();
        return new ConnectorLeaseResponse(attemptId, attempt.getLeaseExpiresAt());
    }
    private AccountingSource lockAndAuthorize(ConnectorIdentity identity) {
        var source = sources.findLockedForSyncById(identity.accountingSourceId()).orElseThrow(ConnectorCredentialUnavailableException::new);
        if (!credentials.isCurrent(identity) || source.getConnectionMode() != AccountingConnectionMode.CONNECTOR)
            throw new ConnectorCredentialUnavailableException();
        return source;
    }
    private SyncAttempt requireAttempt(ConnectorIdentity identity, UUID attemptId) {
        var attempt = attempts.findById(attemptId).filter(value -> value.getAgent().equals(agent(identity)))
                .orElseThrow(() -> new SaasResourceNotFoundException("Work"));
        runs.findByIdAndAccountingSourceId(attempt.getSyncRunId(), identity.accountingSourceId())
                .orElseThrow(() -> new SaasResourceNotFoundException("Work"));
        return attempt;
    }
    private SyncClaimAgent agent(ConnectorIdentity identity) { return new SyncClaimAgent(identity.credentialId(), identity.version()); }
}
