package com.finemp.accounting.sync.publication;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.account.*;
import com.finemp.accounting.canonical.persistence.*;
import com.finemp.accounting.canonical.v2.*;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
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
    private static final Set<AccountingDataset> SUPPORTED_V2_DATASETS = Set.of(
            AccountingDataset.CHART,
            AccountingDataset.MOVEMENTS,
            AccountingDataset.DAILY_TOTALS,
            AccountingDataset.MONTHLY_TOTALS,
            AccountingDataset.ORIGIN_CATALOG);
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
    private final AccountingDatasetCoverageService coverage;
    private final CanonicalV2DatasetStore canonicalV2Store;
    private final AccountingMovementBatchPublisher movementBatchPublisher;

    public AccountingPublicationTransaction(SyncRunRepository runs, AccountingSourceRepository sources,
            AccountingAccountRepository accounts, AccountingMovementRepository movements, SyncRunEventRepository events,
            CompanyPermissionAuthorizationService permissions, SyncControlTransactionGuard transactions,
            Clock clock, @Value("${finemp.sync.publication.max-records:25000}") int maxRecords, SyncAttemptRepository attempts,
            ObjectProvider<SyncAgentAuthorization> agents,
            AccountingDatasetCoverageService coverage,
            CanonicalV2DatasetStore canonicalV2Store,
            AccountingMovementBatchPublisher movementBatchPublisher) {
        if (maxRecords < 1) throw new IllegalArgumentException("Invalid publication resource limit.");
        this.runs = runs; this.sources = sources; this.accounts = accounts; this.movements = movements;
        this.events = events; this.permissions = permissions; this.transactions = transactions;
        this.clock = clock; this.maxRecords = maxRecords; this.attempts = attempts;
        this.agents = agents; this.coverage = coverage; this.canonicalV2Store = canonicalV2Store;
        this.movementBatchPublisher = movementBatchPublisher;
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
        var authorized = requireAuthorizedPublication(claim, observedIdentity);
        var sourceId = authorized.sourceId();
        var source = authorized.source();
        var run = authorized.run();
        var attempt = authorized.attempt();

        var batch = CanonicalBatchValidator.validate(claim.request(), records, maxRecords);
        var publication = movementBatchPublisher.publish(source, batch);
        coverage.record(
                sourceId,
                AccountingDataset.MOVEMENTS,
                run.getStartDate(),
                run.getEndDate());
        var completedAt = clock.instant();
        if (!attempt.isLive(completedAt)) throw new StaleSyncClaimException();
        attempt.succeed(completedAt);
        run.succeed(
                batch.readCount(),
                publication.insertedCount(),
                publication.skippedCount(),
                completedAt);
        events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, clock.instant()));
        runs.flush(); // Includes movements, ownership FKs, measured counters and terminal audit.
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publishConnectorV2(ClaimedSyncRun claim, CanonicalAccountingBatch batch) {
        if (!claim.agent().isConnector()) throw new StaleSyncClaimException();
        var authorized = requireAuthorizedPublication(claim, batch == null ? null : batch.observedIdentity());
        var sourceId = authorized.sourceId();
        var source = authorized.source();
        var run = authorized.run();
        var attempt = authorized.attempt();

        try {
            var validated = CanonicalV2BatchValidator.validate(
                    batch,
                    run.getStartDate(),
                    run.getEndDate(),
                    maxRecords);
            if (!SUPPORTED_V2_DATASETS.containsAll(validated.capabilities().datasets())) {
                throw rejected(SyncFailureCode.INVALID_PAYLOAD);
            }

            Map<String, ? extends CanonicalAccountDescriptor> accountAuthority =
                    accountAuthority(validated);
            var legacyRows = validated.entries() == null
                    ? List.<ImportedAccountingMovement>of()
                    : CanonicalV2ToLegacyMovementsConverter.convert(
                            validated.entries().entries(),
                            accountAuthority);

            var knownAccounts = publishV2Accounts(sourceId, source, accountAuthority, validated);
            int inserted = publishV2LegacyMovements(sourceId, knownAccounts, legacyRows);

            persistV2Datasets(sourceId, validated);

            var completedAt = clock.instant();
            recordV2Coverage(sourceId, run, validated, completedAt);

            if (!attempt.isLive(completedAt)) throw new StaleSyncClaimException();
            attempt.succeed(completedAt);
            run.succeed(legacyRows.size(), inserted, legacyRows.size() - inserted, completedAt);
            events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, clock.instant()));
            runs.flush();
        } catch (CanonicalPersistenceConflictException conflict) {
            throw rejected(SyncFailureCode.DATA_CONFLICT);
        }
    }

    private AuthorizedPublication requireAuthorizedPublication(
            ClaimedSyncRun claim,
            ExternalCompanyIdentity observedIdentity) {
        transactions.requireReadCommitted();
        var sourceId = runs.findSourceIdByRunId(claim.runId())
                .orElseThrow(StaleSyncClaimException::new);
        if (!sourceId.equals(claim.request().accountingSourceId())) throw new StaleSyncClaimException();

        var source = sources.findLockedForSyncById(sourceId)
                .orElseThrow(StaleSyncClaimException::new);
        var run = runs.findByIdAndAccountingSourceId(claim.runId(), sourceId)
                .orElseThrow(StaleSyncClaimException::new);

        if (!agentIsCurrent(claim.agent(), source)) throw new StaleSyncClaimException();
        if (run.getStatus() != SyncRunStatus.RUNNING
                || run.getVersion() != claim.version()
                || run.getMode() != claim.mode()
                || !run.getStartDate().equals(claim.request().startDate())
                || !run.getEndDate().equals(claim.request().endDate())) {
            throw new StaleSyncClaimException();
        }
        if (run.getMode() != SyncRunMode.INITIAL) {
            throw rejected(SyncFailureCode.UNSUPPORTED_MODE);
        }

        var attempt = attempts.findBySyncRunIdAndFenceVersion(run.getId(), claim.version())
                .filter(value -> value.getAgent().equals(claim.agent()) && value.isLive(clock.instant()))
                .orElseThrow(StaleSyncClaimException::new);

        if (!source.isActive()) throw rejected(SyncFailureCode.SOURCE_NOT_AVAILABLE);
        if (!source.getCompany().getId().equals(claim.request().companyId())
                || source.getType() != claim.request().sourceType()
                || !Objects.equals(source.getExternalReference(), claim.request().externalCompanyReference())
                || !Objects.equals(source.getCompany().getCnpj(), claim.request().expectedCompanyCnpj())) {
            throw rejected(SyncFailureCode.IDENTITY_MISMATCH);
        }
        if (!permissions.hasFreshPermission(
                run.getRequestedByUserId(),
                source.getCompany().getId(),
                CompanyPermission.QUESTOR_SYNC)) {
            throw rejected(SyncFailureCode.ACCESS_REVOKED);
        }
        if (claim.agent().isConnector()
                && (observedIdentity == null || !observedIdentity.matches(claim.request()))) {
            throw rejected(SyncFailureCode.IDENTITY_MISMATCH);
        }
        return new AuthorizedPublication(sourceId, source, run, attempt);
    }

    private Map<String, ? extends CanonicalAccountDescriptor> accountAuthority(
            CanonicalAccountingBatch batch) {
        if (batch.capabilities().datasets().contains(AccountingDataset.CHART)) {
            var accounts = new LinkedHashMap<String, CanonicalAccountingAccount>();
            for (var account : batch.chart().accounts()) {
                var previous = accounts.putIfAbsent(account.sourceAccountId(), account);
                if (previous != null && !previous.equals(account)) {
                    throw rejected(SyncFailureCode.DATA_CONFLICT);
                }
            }
            return accounts;
        }
        var accounts = new LinkedHashMap<String, CanonicalObservedAccountDescriptor>();
        if (batch.observedAccountDescriptors() != null) {
            for (var account : batch.observedAccountDescriptors()) {
                var previous = accounts.putIfAbsent(account.sourceAccountId(), account);
                if (previous != null && !previous.equals(account)) {
                    throw rejected(SyncFailureCode.DATA_CONFLICT);
                }
            }
        }
        return accounts;
    }

    private Map<String, AccountingAccount> publishV2Accounts(
            UUID sourceId,
            AccountingSource source,
            Map<String, ? extends CanonicalAccountDescriptor> descriptors,
            CanonicalAccountingBatch batch) {
        var knownAccounts = loadAccounts(sourceId, descriptors.keySet());
        var newAccounts = new ArrayList<AccountingAccount>();

        for (var descriptor : descriptors.values()) {
            String accountKey = legacyAccountKey(descriptor);
            var existing = knownAccounts.get(descriptor.sourceAccountId());
            if (existing != null) {
                if (!sameLegacyAccountKey(existing.getAccountKey(), accountKey)
                        || !sameLegacyDescription(existing.getDescription(), descriptor.description())) {
                    throw rejected(SyncFailureCode.DATA_CONFLICT);
                }
            } else {
                newAccounts.add(new AccountingAccount(
                        source,
                        descriptor.sourceAccountId(),
                        accountKey,
                        descriptor.description()));
            }
        }

        for (var account : accounts.saveAll(newAccounts)) {
            knownAccounts.put(account.getExternalAccountId(), account);
        }

        if (batch.capabilities().datasets().contains(AccountingDataset.CHART)) {
            for (var account : batch.chart().accounts()) {
                canonicalV2Store.persistAccountSourceAttributes(sourceId, account);
            }
        }
        return knownAccounts;
    }

    private int publishV2LegacyMovements(
            UUID sourceId,
            Map<String, AccountingAccount> knownAccounts,
            List<ImportedAccountingMovement> rows) {
        var knownMovements = loadMovements(sourceId, rows);
        var toInsert = new ArrayList<ImportedAccountingMovement>();

        for (var row : rows) {
            var identity = new ValidatedAccountingBatch.MovementIdentity(
                    row.sourceEntryKey(),
                    row.side());
            var existing = knownMovements.get(identity);
            if (existing == null) {
                toInsert.add(row);
            } else if (!same(existing, row)) {
                throw rejected(SyncFailureCode.DATA_CONFLICT);
            }
        }

        var inserts = toInsert.stream()
                .map(row -> new AccountingMovement(
                        knownAccounts.get(row.externalAccountId()),
                        row.sourceEntryKey(),
                        row.side(),
                        row.accountingDate(),
                        row.amount(),
                        row.history()))
                .toList();
        movements.saveAll(inserts);
        return inserts.size();
    }

    private void persistV2Datasets(UUID sourceId, CanonicalAccountingBatch batch) {
        if (batch.entries() != null && batch.entries().metadata() != null) {
            for (var metadata : batch.entries().metadata()) {
                canonicalV2Store.persistEntrySourceMetadata(sourceId, metadata);
            }
        }
        if (batch.dailyTotals() != null) {
            for (var total : batch.dailyTotals()) {
                canonicalV2Store.persistDailyTotals(sourceId, total);
            }
        }
        if (batch.monthlyTotals() != null) {
            for (var total : batch.monthlyTotals()) {
                canonicalV2Store.persistMonthlyTotals(sourceId, total);
            }
        }
        if (batch.originCatalog() != null) {
            for (var entry : batch.originCatalog()) {
                canonicalV2Store.persistOriginCatalogEntry(sourceId, entry);
            }
        }
    }

    private void recordV2Coverage(
            UUID sourceId,
            SyncRun run,
            CanonicalAccountingBatch batch,
            java.time.Instant completedAt) {
        var datasets = batch.capabilities().datasets();
        if (datasets.contains(AccountingDataset.CHART)) {
            coverage.recordSnapshot(sourceId, AccountingDataset.CHART, null, completedAt);
        }
        if (datasets.contains(AccountingDataset.MOVEMENTS)) {
            coverage.recordInterval(
                    sourceId,
                    AccountingDataset.MOVEMENTS,
                    null,
                    run.getStartDate(),
                    run.getEndDate());
        }
        if (datasets.contains(AccountingDataset.DAILY_TOTALS)) {
            coverage.recordInterval(
                    sourceId,
                    AccountingDataset.DAILY_TOTALS,
                    null,
                    run.getStartDate(),
                    run.getEndDate());
        }
        if (datasets.contains(AccountingDataset.MONTHLY_TOTALS)) {
            var interval = completeMonths(run.getStartDate(), run.getEndDate());
            if (interval == null) throw rejected(SyncFailureCode.INVALID_PAYLOAD);
            for (var total : batch.monthlyTotals()) {
                if (total.month().isBefore(java.time.YearMonth.from(interval.from()))
                        || total.month().isAfter(java.time.YearMonth.from(interval.to()))) {
                    throw rejected(SyncFailureCode.INVALID_PAYLOAD);
                }
            }
            coverage.recordInterval(
                    sourceId,
                    AccountingDataset.MONTHLY_TOTALS,
                    null,
                    interval.from(),
                    interval.to());
        }
        if (datasets.contains(AccountingDataset.ORIGIN_CATALOG)) {
            coverage.recordSnapshot(sourceId, AccountingDataset.ORIGIN_CATALOG, null, completedAt);
        }
    }

    private CoverageInterval completeMonths(java.time.LocalDate start, java.time.LocalDate end) {
        var firstMonth = start.getDayOfMonth() == 1
                ? java.time.YearMonth.from(start)
                : java.time.YearMonth.from(start).plusMonths(1);
        var endMonth = java.time.YearMonth.from(end);
        var lastMonth = end.equals(endMonth.atEndOfMonth())
                ? endMonth
                : endMonth.minusMonths(1);
        if (firstMonth.isAfter(lastMonth)) return null;
        return new CoverageInterval(firstMonth.atDay(1), lastMonth.atEndOfMonth());
    }

    private boolean sameLegacyAccountKey(String existing, String canonical) {
        return Objects.equals(normalizeLegacyAccountKey(existing), normalizeLegacyAccountKey(canonical));
    }

    private String normalizeLegacyAccountKey(String value) {
        if (value == null) return null;
        return value.strip().replaceAll("\\s*\\.\\s*", ".");
    }

    private boolean sameLegacyDescription(String existing, String canonical) {
        return Objects.equals(stripLegacyRightPadding(existing), stripLegacyRightPadding(canonical));
    }

    private String stripLegacyRightPadding(String value) {
        return value == null ? null : value.stripTrailing();
    }

    private String legacyAccountKey(CanonicalAccountDescriptor descriptor) {
        String value = descriptor.classificationCode() + "." + descriptor.sourceAccountId();
        if (value.codePointCount(0, value.length()) > 255) {
            throw rejected(SyncFailureCode.INVALID_PAYLOAD);
        }
        return value;
    }

    private record AuthorizedPublication(
            UUID sourceId,
            AccountingSource source,
            SyncRun run,
            SyncAttempt attempt) {
    }

    private record CoverageInterval(
            java.time.LocalDate from,
            java.time.LocalDate to) {
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

