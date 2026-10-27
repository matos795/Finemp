package com.finemp.connector.completion;

import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.*;
import com.finemp.accounting.sync.publication.AccountingPublicationTransaction;
import com.finemp.connector.ConnectorIdentity;
import com.finemp.connector.work.ConnectorWorkNotCurrentException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
public class ConnectorCompletionService {
    private final ConnectorCompletionContext context;
    private final ConnectorBatchDecoder decoder;
    private final AccountingPublicationTransaction publication;
    private final SyncExecutionLifecycle lifecycle;
    public ConnectorCompletionService(ConnectorCompletionContext context, ConnectorBatchDecoder decoder,
            AccountingPublicationTransaction publication, SyncExecutionLifecycle lifecycle) {
        this.context = context; this.decoder = decoder; this.publication = publication; this.lifecycle = lifecycle;
    }
    @Transactional(propagation = Propagation.NEVER)
    public ConnectorCompletionOutcome complete(ConnectorIdentity identity, UUID attemptId, InputStream input) {
        var claim = context.currentClaim(identity, attemptId);
        var batch = decoder.decode(input); // No PostgreSQL transaction or lock is held during parsing.
        try { publication.publishConnector(claim, batch.observedIdentity(), batch.movements()); }
        catch (StaleSyncClaimException stale) { throw new ConnectorWorkNotCurrentException(); }
        catch (SyncPublicationRejectedException rejected) { return failed(identity, attemptId, claim, rejected.getCode()); }
        catch (RuntimeException unavailable) { return failed(identity, attemptId, claim, SyncFailureCode.PUBLICATION_FAILED); }
        return new ConnectorCompletionOutcome(200, context.result(identity, attemptId));
    }
    @Transactional(propagation = Propagation.NEVER)
    public ConnectorCompletionOutcome reportFailure(ConnectorIdentity identity, UUID attemptId, InputStream input) {
        var claim = context.currentClaim(identity, attemptId);
        return failed(identity, attemptId, claim, decoder.decodeFailure(input));
    }
    private ConnectorCompletionOutcome failed(ConnectorIdentity identity, UUID attemptId, ClaimedSyncRun claim, SyncFailureCode code) {
        var disposition = lifecycle.failIfCurrent(claim, code); // A separate transaction after the publication rolled back.
        if (disposition == SyncFailureDisposition.STALE) throw new ConnectorWorkNotCurrentException();
        return new ConnectorCompletionOutcome(disposition == SyncFailureDisposition.RETRY_SCHEDULED ? 202 : 422, context.result(identity, attemptId));
    }
}
