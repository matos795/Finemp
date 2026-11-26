package com.finemp.accounting.fileimport;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.sync.publication.CanonicalBatchValidator;
import com.finemp.accounting.sync.publication.ValidatedAccountingBatch;
import java.util.LinkedHashMap;

final class AccountingFileImportNormalizer {
    private AccountingFileImportNormalizer() {
    }

    static ValidatedAccountingBatch normalize(GeneralLedgerParsedFile parsed, int maxRecords) {
        if (parsed == null || !parsed.errors().isEmpty() || parsed.invalidRowCount() > 0) {
            throw new AccountingFilePreviewException(
                    "O arquivo possui linhas inválidas e não pode ser importado.");
        }

        var rows = parsed.movements().stream()
                .map(row -> new ImportedAccountingMovement(
                        row.sourceEntryKey(),
                        row.accountingDate(),
                        row.sourceAccountId(),
                        observedAccountKey(row.sourceAccountId()),
                        row.accountDescription(),
                        row.side(),
                        row.amount(),
                        row.history()))
                .toList();

        var validated = CanonicalBatchValidator.validate(
                parsed.periodStart(),
                parsed.periodEnd(),
                rows,
                maxRecords);

        var accounts = new LinkedHashMap<String, ValidatedAccountingBatch.AccountDescriptor>(
                validated.accounts());

        for (var account : parsed.accounts()) {
            if (!identity(account.sourceAccountId())
                    || !identity(account.description())) {
                throw new AccountingFilePreviewException(
                        "O arquivo possui uma conta observada inválida.");
            }

            var descriptor = new ValidatedAccountingBatch.AccountDescriptor(
                    account.sourceAccountId(),
                    observedAccountKey(account.sourceAccountId()),
                    account.description());
            var previous = accounts.putIfAbsent(account.sourceAccountId(), descriptor);
            if (previous != null && !previous.equals(descriptor)) {
                throw new AccountingFilePreviewException(
                        "O arquivo possui dados divergentes para a mesma conta.");
            }
        }

        return new ValidatedAccountingBatch(
                parsed.movements().size(),
                validated.uniqueMovements(),
                accounts);
    }

    /**
     * The file exposes no documented equivalent of Questor CLASSIFCONTA.
     * The raw Conta code is therefore preserved as an opaque observed technical key.
     */
    private static String observedAccountKey(String sourceAccountId) {
        return sourceAccountId;
    }

    private static boolean identity(String value) {
        return value != null
                && !value.isBlank()
                && value.codePointCount(0, value.length()) <= 255;
    }
}
