package com.finemp.accounting.sync.publication;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.account.*;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.movement.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.SyncControlTransactionGuard;
import com.finemp.accounting.sync.execution.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnExpression("${finemp.sync.executor.enabled:false} || ${finemp.connector.protocol.enabled:false}")
public class AccountingPublicationTransaction {
    private static final int LOOKUP_CHUNK = 500;
    private final SyncRunRepository runs;
    private final AccountingSourceRepository sources;
    private final AccountingAccountRepository accounts;
    private final AccountingMovementRepository movements;
    private final SyncRunEventRepository events;
    private final CompanyPermissionAuthorizationService permissions;
    private final SyncControlTransactionGuard transactions;
    private final Clock clock;
    private final int maxRecords;
    private final SyncAttemptRepository attempts;
    private final ObjectProvider<SyncAgentAuthorization> agents;

    public AccountingPublicationTransaction(SyncRunRepository runs, AccountingSourceRepository sources,
            AccountingAccountRepository accounts, AccountingMovementRepository movements, SyncRunEventRepository events,
            CompanyPermissionAuthorizationService permissions, SyncControlTransactionGuard transactions,
            Clock clock, @Value("${finemp.sync.publication.max-records:25000}") int maxRecords, SyncAttemptRepository attempts,
            ObjectProvider<SyncAgentAuthorization> agents) {
        if (maxRecords < 1) throw new IllegalArgumentException("Invalid publication resource limit.");
        this.runs = runs; this.sources = sources; this.accounts = accounts; this.movements = movements;
        this.events = events; this.permissions = permissions; this.transactions = transactions;
        this.clock = clock; this.maxRecords = maxRecords; this.attempts = attempts;
        this.agents = agents;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publish(ClaimedSyncRun claim, List<ImportedAccountingMovement> records) {
        if (claim.agent().isConnector()) throw new StaleSyncClaimException();
        publishAuthorized(claim, records, null);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publishConnector(ClaimedSyncRun claim, ExternalCompanyIdentity observedIdentity, List<ImportedAccountingMovement> records) {
        if (!claim.agent().isConnector()) throw new StaleSyncClaimException();
        publishAuthorized(claim, records, observedIdentity);
    }
    private void publishAuthorized(ClaimedSyncRun claim, List<ImportedAccountingMovement> records, ExternalCompanyIdentity observedIdentity) {
        transactions.requireReadCommitted();
        var sourceId = runs.findSourceIdByRunId(claim.runId()).orElseThrow(StaleSyncClaimException::new);
        if (!sourceId.equals(claim.request().accountingSourceId())) throw new StaleSyncClaimException();
        var source = sources.findLockedForSyncById(sourceId).orElseThrow(StaleSyncClaimException::new);
        var run = runs.findByIdAndAccountingSourceId(claim.runId(), sourceId).orElseThrow(StaleSyncClaimException::new);
        if (!agentIsCurrent(claim.agent(), source)) throw new StaleSyncClaimException();
        if (run.getStatus() != SyncRunStatus.RUNNING || run.getVersion() != claim.version() || run.getMode() != claim.mode()
                || !run.getStartDate().equals(claim.request().startDate()) || !run.getEndDate().equals(claim.request().endDate()))
            throw new StaleSyncClaimException();
        if (run.getMode() != SyncRunMode.INITIAL) throw rejected(SyncFailureCode.UNSUPPORTED_MODE);
        var attempt = attempts.findBySyncRunIdAndFenceVersion(run.getId(), claim.version())
                .filter(value -> value.getAgent().equals(claim.agent()) && value.isLive(clock.instant())).orElseThrow(StaleSyncClaimException::new);
        if (!source.isActive()) throw rejected(SyncFailureCode.SOURCE_NOT_AVAILABLE);
        if (!source.getCompany().getId().equals(claim.request().companyId()) || source.getType() != claim.request().sourceType()
                || !Objects.equals(source.getExternalReference(), claim.request().externalCompanyReference())
                || !Objects.equals(source.getCompany().getCnpj(), claim.request().expectedCompanyCnpj()))
            throw rejected(SyncFailureCode.IDENTITY_MISMATCH);
        if (!permissions.hasFreshPermission(run.getRequestedByUserId(), source.getCompany().getId(), CompanyPermission.QUESTOR_SYNC))
            throw rejected(SyncFailureCode.ACCESS_REVOKED);
        if (claim.agent().isConnector() && (observedIdentity == null || !observedIdentity.matches(claim.request())))
            throw rejected(SyncFailureCode.IDENTITY_MISMATCH);

        var batch = CanonicalBatchValidator.validate(claim.request(), records, maxRecords);
        var knownAccounts = loadAccounts(sourceId, batch.accounts().keySet());
        for (var descriptor : batch.accounts().values()) {
            var existing = knownAccounts.get(descriptor.externalId());
            if (existing != null && (!Objects.equals(existing.getAccountKey(), descriptor.accountKey())
                    || !Objects.equals(existing.getDescription(), descriptor.description()))) throw rejected(SyncFailureCode.DATA_CONFLICT);
        }
        var knownMovements = loadMovements(sourceId, batch.uniqueMovements());
        var toInsert = new ArrayList<ImportedAccountingMovement>();
        for (var row : batch.uniqueMovements()) {
            var existing = knownMovements.get(new ValidatedAccountingBatch.MovementIdentity(row.sourceEntryKey(), row.side()));
            if (existing == null) toInsert.add(row);
            else if (!same(existing, row)) throw rejected(SyncFailureCode.DATA_CONFLICT);
        }

        // Nothing is written before all row/account conflicts have been checked.
        var newAccounts = new ArrayList<AccountingAccount>();
        for (var descriptor : batch.accounts().values())
            if (!knownAccounts.containsKey(descriptor.externalId()))
                newAccounts.add(new AccountingAccount(source, descriptor.externalId(), descriptor.accountKey(), descriptor.description()));
        for (var account : accounts.saveAll(newAccounts)) knownAccounts.put(account.getExternalAccountId(), account);
        var inserts = toInsert.stream().map(row -> new AccountingMovement(knownAccounts.get(row.externalAccountId()),
                row.sourceEntryKey(), row.side(), row.accountingDate(), row.amount(), row.history())).toList();
        movements.saveAll(inserts);
        var completedAt = clock.instant();
        if (!attempt.isLive(completedAt)) throw new StaleSyncClaimException();
        attempt.succeed(completedAt);
        run.succeed(batch.readCount(), inserts.size(), batch.readCount() - inserts.size(), completedAt);
        events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, clock.instant()));
        runs.flush(); // Includes movements, ownership FKs, measured counters and terminal audit.
    }

    private boolean agentIsCurrent(SyncClaimAgent agent, AccountingSource source) {
        if (!agent.isConnector()) return source.getConnectionMode() == AccountingConnectionMode.DIRECT;
        var authorization = agents.getIfUnique();
        return source.getConnectionMode() == AccountingConnectionMode.CONNECTOR && authorization != null
                && authorization.isCurrent(agent, source.getId());
    }
    private Map<String, AccountingAccount> loadAccounts(UUID sourceId, Set<String> keys) {
        var result = new HashMap<String, AccountingAccount>(); var list = new ArrayList<>(keys);
        for (int from = 0; from < list.size(); from += LOOKUP_CHUNK)
            for (var account : accounts.findAllByAccountingSourceIdAndExternalAccountIdIn(sourceId,
                    list.subList(from, Math.min(from + LOOKUP_CHUNK, list.size()))))
                if (result.putIfAbsent(account.getExternalAccountId(), account) != null) throw rejected(SyncFailureCode.DATA_CONFLICT);
        return result;
    }
    private Map<ValidatedAccountingBatch.MovementIdentity, AccountingMovement> loadMovements(UUID sourceId, List<ImportedAccountingMovement> rows) {
        var result = new HashMap<ValidatedAccountingBatch.MovementIdentity, AccountingMovement>();
        var keys = new ArrayList<>(new LinkedHashSet<>(rows.stream().map(ImportedAccountingMovement::sourceEntryKey).toList()));
        for (int from = 0; from < keys.size(); from += LOOKUP_CHUNK)
            for (var movement : movements.findAllForPublication(sourceId, keys.subList(from, Math.min(from + LOOKUP_CHUNK, keys.size()))))
                if (result.putIfAbsent(new ValidatedAccountingBatch.MovementIdentity(movement.getSourceEntryKey(), movement.getSide()), movement) != null)
                    throw rejected(SyncFailureCode.DATA_CONFLICT);
        return result;
    }
    private boolean same(AccountingMovement existing, ImportedAccountingMovement row) {
        return existing.getAccount().getExternalAccountId().equals(row.externalAccountId())
                && existing.getAccountingDate().equals(row.accountingDate())
                && existing.getAmount().compareTo(row.amount()) == 0 && Objects.equals(existing.getHistory(), row.history());
    }
    private SyncPublicationRejectedException rejected(SyncFailureCode code) { return new SyncPublicationRejectedException(code); }
}

