package com.finemp.accounting.sync.execution;

import com.finemp.accounting.reading.AccountingCompanyIdentityReader;
import com.finemp.accounting.reading.AccountingSourceReaderRegistry;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;
import com.finemp.accounting.reading.AccountingIdentityMismatchException;
import com.finemp.accounting.reading.AccountingIdentityConfigurationException;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.sync.SyncFailureCode;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "finemp.sync.executor", name = "enabled", havingValue = "true")
public class SyncRunExecutor {
    private final SyncExecutionLifecycle lifecycle;
    private final AccountingCompanyIdentityReader identities;
    private final AccountingSourceReaderRegistry readers;
    private final SyncPublicationPort publication;
    public SyncRunExecutor(SyncExecutionLifecycle lifecycle, AccountingCompanyIdentityReader identities,
            AccountingSourceReaderRegistry readers, SyncPublicationPort publication) {
        this.lifecycle = lifecycle; this.identities = identities; this.readers = readers; this.publication = publication;
    }
    @Transactional(propagation = Propagation.NEVER)
    public SyncExecutionOutcome execute(UUID runId) {
        var claimed = lifecycle.claim(runId);
        if (claimed.isEmpty()) return SyncExecutionOutcome.NOT_CLAIMED;
        return executeClaim(claimed.get());
    }
    /** Internal worker entry point. Publication rechecks this claim; this is never a browser endpoint. */
    @Transactional(propagation = Propagation.NEVER)
    public SyncExecutionOutcome executeClaim(ClaimedSyncRun claim) {
        if (claim.agent().isConnector()) return SyncExecutionOutcome.STALE;
        ExternalCompanyIdentity identity;
        try { identity = identities.readIdentity(claim.request()); }
        catch (AccountingIdentityMismatchException mismatch) { return fail(claim, SyncFailureCode.IDENTITY_MISMATCH); }
        catch (AccountingIdentityConfigurationException unsupported) { return fail(claim, SyncFailureCode.SOURCE_NOT_AVAILABLE); }
        catch (RuntimeException readFailure) { return fail(claim, SyncFailureCode.READ_FAILED); }
        if (identity == null || !identity.matches(claim.request())) return fail(claim, SyncFailureCode.IDENTITY_MISMATCH);
        List<ImportedAccountingMovement> movements;
        try { movements = readers.readMovements(claim.request()); }
        catch (AccountingIdentityMismatchException mismatch) { return fail(claim, SyncFailureCode.IDENTITY_MISMATCH); }
        catch (AccountingIdentityConfigurationException unsupported) { return fail(claim, SyncFailureCode.SOURCE_NOT_AVAILABLE); }
        catch (AccountingReadLimitExceededException limit) { return fail(claim, SyncFailureCode.RESOURCE_LIMIT); }
        catch (RuntimeException readFailure) { return fail(claim, SyncFailureCode.READ_FAILED); }
        try { publication.publish(claim, movements); }
        catch (StaleSyncClaimException stale) { return SyncExecutionOutcome.STALE; }
        catch (SyncPublicationRejectedException rejected) { return fail(claim, rejected.getCode()); }
        catch (RuntimeException publicationFailure) { return fail(claim, SyncFailureCode.PUBLICATION_FAILED); }
        return SyncExecutionOutcome.PUBLISHED;
    }
    private SyncExecutionOutcome fail(ClaimedSyncRun claim, SyncFailureCode code) {
        return switch (lifecycle.failIfCurrent(claim, code)) {
            case STALE -> SyncExecutionOutcome.STALE;
            case RETRY_SCHEDULED -> SyncExecutionOutcome.RETRY_SCHEDULED;
            case TERMINAL_FAILURE -> SyncExecutionOutcome.FAILED;
        };
    }
}

