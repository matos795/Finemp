package com.finemp.accounting.canonical.v2;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CanonicalV2ToLegacyMovementsConverter {
    private CanonicalV2ToLegacyMovementsConverter() {
    }

    public static List<ImportedAccountingMovement> convert(
            List<CanonicalAccountingEntry> entries,
            Map<String, CanonicalAccountingAccount> accountsById) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(accountsById, "accountsById");

        var uniqueEntries = new LinkedHashMap<String, CanonicalAccountingEntry>();
        for (var entry : entries) {
            if (entry == null || entry.sourceEntryKey() == null) {
                throw new IllegalArgumentException("Canonical entry is invalid.");
            }
            var previous = uniqueEntries.putIfAbsent(entry.sourceEntryKey(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new SyncPublicationRejectedException(SyncFailureCode.DATA_CONFLICT);
            }
        }

        var movements = new ArrayList<ImportedAccountingMovement>();
        for (var entry : uniqueEntries.values()) {
            if (entry.debitAccountId() != null) {
                movements.add(toMovement(entry, entry.debitAccountId(), AccountingMovementSide.DEBIT, accountsById));
            }
            if (entry.creditAccountId() != null) {
                movements.add(toMovement(entry, entry.creditAccountId(), AccountingMovementSide.CREDIT, accountsById));
            }
        }
        return List.copyOf(movements);
    }

    private static ImportedAccountingMovement toMovement(
            CanonicalAccountingEntry entry,
            String sourceAccountId,
            AccountingMovementSide side,
            Map<String, CanonicalAccountingAccount> accountsById) {
        var account = accountsById.get(sourceAccountId);
        if (account == null) {
            throw new IllegalArgumentException("Canonical account descriptor is missing.");
        }

        return new ImportedAccountingMovement(
                entry.sourceEntryKey(),
                entry.accountingDate(),
                sourceAccountId,
                account.classificationCode() + "." + sourceAccountId,
                account.description(),
                side,
                entry.amount(),
                entry.historyText());
    }
}
