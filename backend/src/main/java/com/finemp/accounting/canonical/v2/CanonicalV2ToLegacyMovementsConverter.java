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
import java.math.BigDecimal;

public final class CanonicalV2ToLegacyMovementsConverter {
    private CanonicalV2ToLegacyMovementsConverter() {
    }

    public static List<ImportedAccountingMovement> convert(
            List<CanonicalAccountingEntry> entries,
            Map<String, ? extends CanonicalAccountDescriptor> accountsById) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(accountsById, "accountsById");

        var uniqueEntries = new LinkedHashMap<String, CanonicalAccountingEntry>();
        for (var entry : entries) {
            if (entry == null || entry.sourceEntryKey() == null) {
                throw new IllegalArgumentException("Canonical entry is invalid.");
            }
            var previous = uniqueEntries.putIfAbsent(entry.sourceEntryKey(), entry);
            if (previous != null && !sameEntry(previous, entry)) {
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

    private static boolean sameEntry(CanonicalAccountingEntry first, CanonicalAccountingEntry second) {
        return Objects.equals(first.accountingDate(), second.accountingDate())
                && Objects.equals(first.debitAccountId(), second.debitAccountId())
                && Objects.equals(first.creditAccountId(), second.creditAccountId())
                && sameAmount(first.amount(), second.amount())
                && Objects.equals(first.establishmentCode(), second.establishmentCode())
                && Objects.equals(first.historyText(), second.historyText());
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private static ImportedAccountingMovement toMovement(
            CanonicalAccountingEntry entry,
            String sourceAccountId,
            AccountingMovementSide side,
            Map<String, ? extends CanonicalAccountDescriptor> accountsById) {
        var account = accountsById.get(sourceAccountId);
        if (account == null) {
            throw new SyncPublicationRejectedException(SyncFailureCode.INVALID_PAYLOAD);
        }

        String accountKey = account.classificationCode() + "." + sourceAccountId;
        if (accountKey.codePointCount(0, accountKey.length()) > 255) {
            throw new SyncPublicationRejectedException(SyncFailureCode.INVALID_PAYLOAD);
        }

        return new ImportedAccountingMovement(
                entry.sourceEntryKey(),
                entry.accountingDate(),
                sourceAccountId,
                accountKey,
                account.description(),
                side,
                entry.amount(),
                entry.historyText());
    }
}
