package com.finemp.accounting.sync.publication;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.math.RoundingMode;
import java.util.*;

public final class CanonicalBatchValidator {
    private CanonicalBatchValidator() { }
    public static ValidatedAccountingBatch validate(AccountingReadRequest request, List<ImportedAccountingMovement> movements, int maxRecords) {
        if (maxRecords < 1) throw new IllegalArgumentException("Invalid batch resource limit.");
        if (request == null || movements == null) throw invalid();
        if (movements.size() > maxRecords) throw new SyncPublicationRejectedException(SyncFailureCode.RESOURCE_LIMIT);
        var unique = new LinkedHashMap<ValidatedAccountingBatch.MovementIdentity, ImportedAccountingMovement>();
        var accounts = new LinkedHashMap<String, ValidatedAccountingBatch.AccountDescriptor>();
        for (var row : movements) {
            if (row == null || !identity(row.sourceEntryKey()) || !identity(row.externalAccountId())
                    || !identity(row.accountKey()) || !text(row.accountDescription())
                    || (row.history() != null && !text(row.history())) || row.side() == null
                    || row.accountingDate() == null || row.accountingDate().isBefore(request.startDate())
                    || row.accountingDate().isAfter(request.endDate()) || row.amount() == null) throw invalid();
            java.math.BigDecimal amount;
            try {
                // Check representability BEFORE scale expansion: a short exponent must not allocate an enormous BigInteger.
                var compact = row.amount().signum() == 0 ? java.math.BigDecimal.ZERO : row.amount().stripTrailingZeros();
                if (compact.scale() > 4 || (long) compact.precision() - compact.scale() > 15) throw invalid();
                amount = compact.setScale(4, RoundingMode.UNNECESSARY);
            }
            catch (ArithmeticException unrepresentable) { throw invalid(); }
            if (amount.precision() > 19) throw invalid();
            var normalized = new ImportedAccountingMovement(row.sourceEntryKey(), row.accountingDate(), row.externalAccountId(),
                    row.accountKey(), row.accountDescription(), row.side(), amount, row.history());
            var account = new ValidatedAccountingBatch.AccountDescriptor(row.externalAccountId(), row.accountKey(), row.accountDescription());
            var previousAccount = accounts.putIfAbsent(row.externalAccountId(), account);
            if (previousAccount != null && !previousAccount.equals(account)) throw conflict();
            var previous = unique.putIfAbsent(new ValidatedAccountingBatch.MovementIdentity(row.sourceEntryKey(), row.side()), normalized);
            if (previous != null && !previous.equals(normalized)) throw conflict();
        }
        return new ValidatedAccountingBatch(movements.size(), new ArrayList<>(unique.values()), accounts);
    }
    private static boolean text(String value) { return value != null && value.codePointCount(0, value.length()) <= 255; }
    private static boolean identity(String value) { return text(value) && !value.isBlank(); }
    private static SyncPublicationRejectedException invalid() { return new SyncPublicationRejectedException(SyncFailureCode.INVALID_PAYLOAD); }
    private static SyncPublicationRejectedException conflict() { return new SyncPublicationRejectedException(SyncFailureCode.DATA_CONFLICT); }
}

