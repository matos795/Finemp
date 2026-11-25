package com.finemp.accounting.fileimport;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingFileImportIdempotencyGuard {
    private final AccountingFileImportReceiptRepository receipts;

    public AccountingFileImportIdempotencyGuard(AccountingFileImportReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public Optional<AccountingFileImportReceipt> findMatching(
            UUID accountingSourceId,
            UUID idempotencyKey,
            String fingerprint) {
        if (accountingSourceId == null
                || idempotencyKey == null
                || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Accounting file import identity is invalid.");
        }

        var existing = receipts.find(accountingSourceId, idempotencyKey);
        if (existing.isPresent() && !existing.get().fingerprint().equals(fingerprint)) {
            throw new AccountingFileImportConflictException(
                    AccountingFileImportConflictException.Reason.IDEMPOTENCY_MISMATCH);
        }
        return existing;
    }
}
