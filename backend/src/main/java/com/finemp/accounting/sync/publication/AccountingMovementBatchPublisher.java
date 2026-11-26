package com.finemp.accounting.sync.publication;

import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovement;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared persistence core for validated accounting movement batches.
 * Callers own authorization, source locking, coverage and lifecycle/receipt state.
 */
@Service
public class AccountingMovementBatchPublisher {
    private static final int LOOKUP_CHUNK = 500;

    private final AccountingAccountRepository accounts;
    private final AccountingMovementRepository movements;

    public AccountingMovementBatchPublisher(
            AccountingAccountRepository accounts,
            AccountingMovementRepository movements) {
        this.accounts = accounts;
        this.movements = movements;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result publish(AccountingSource source, ValidatedAccountingBatch batch) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(batch, "batch");
        if (source.getId() == null) {
            throw new IllegalArgumentException("Accounting source must be persisted.");
        }

        UUID sourceId = source.getId();
        var knownAccounts = loadAccounts(sourceId, batch.accounts().keySet());

        for (var descriptor : batch.accounts().values()) {
            var existing = knownAccounts.get(descriptor.externalId());
            if (existing != null
                    && (!Objects.equals(existing.getAccountKey(), descriptor.accountKey())
                    || !Objects.equals(existing.getDescription(), descriptor.description()))) {
                throw rejected(SyncFailureCode.DATA_CONFLICT);
            }
        }

        var knownMovements = loadMovements(sourceId, batch.uniqueMovements());
        var toInsert = new ArrayList<ImportedAccountingMovement>();
        for (var row : batch.uniqueMovements()) {
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

        // No writes before all existing-account and existing-movement conflicts are checked.
        var newAccounts = new ArrayList<AccountingAccount>();
        for (var descriptor : batch.accounts().values()) {
            if (!knownAccounts.containsKey(descriptor.externalId())) {
                newAccounts.add(new AccountingAccount(
                        source,
                        descriptor.externalId(),
                        descriptor.accountKey(),
                        descriptor.description()));
            }
        }
        for (var account : accounts.saveAll(newAccounts)) {
            knownAccounts.put(account.getExternalAccountId(), account);
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

        return new Result(inserts.size(), Math.toIntExact(batch.readCount() - inserts.size()));
    }

    private Map<String, AccountingAccount> loadAccounts(UUID sourceId, java.util.Set<String> keys) {
        var result = new HashMap<String, AccountingAccount>();
        var list = new ArrayList<>(keys);
        for (int from = 0; from < list.size(); from += LOOKUP_CHUNK) {
            for (var account : accounts.findAllByAccountingSourceIdAndExternalAccountIdIn(
                    sourceId,
                    list.subList(from, Math.min(from + LOOKUP_CHUNK, list.size())))) {
                if (result.putIfAbsent(account.getExternalAccountId(), account) != null) {
                    throw rejected(SyncFailureCode.DATA_CONFLICT);
                }
            }
        }
        return result;
    }

    private Map<ValidatedAccountingBatch.MovementIdentity, AccountingMovement> loadMovements(
            UUID sourceId,
            java.util.List<ImportedAccountingMovement> rows) {
        var result = new HashMap<ValidatedAccountingBatch.MovementIdentity, AccountingMovement>();
        var keys = new ArrayList<>(new LinkedHashSet<>(
                rows.stream().map(ImportedAccountingMovement::sourceEntryKey).toList()));
        for (int from = 0; from < keys.size(); from += LOOKUP_CHUNK) {
            for (var movement : movements.findAllForPublication(
                    sourceId,
                    keys.subList(from, Math.min(from + LOOKUP_CHUNK, keys.size())))) {
                var identity = new ValidatedAccountingBatch.MovementIdentity(
                        movement.getSourceEntryKey(),
                        movement.getSide());
                if (result.putIfAbsent(identity, movement) != null) {
                    throw rejected(SyncFailureCode.DATA_CONFLICT);
                }
            }
        }
        return result;
    }

    private boolean same(AccountingMovement existing, ImportedAccountingMovement row) {
        return existing.getAccount().getExternalAccountId().equals(row.externalAccountId())
                && existing.getAccountingDate().equals(row.accountingDate())
                && existing.getAmount().compareTo(row.amount()) == 0
                && Objects.equals(existing.getHistory(), row.history());
    }

    private SyncPublicationRejectedException rejected(SyncFailureCode code) {
        return new SyncPublicationRejectedException(code);
    }

    public record Result(int insertedCount, int skippedCount) {
    }
}
