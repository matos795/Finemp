package com.finemp.accounting.fileimport;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import com.finemp.accounting.sync.publication.AccountingMovementBatchPublisher;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingFileImportConfirmationService {
    private final AccountingSourceRepository sources;
    private final CompanyPermissionAuthorizationService permissions;
    private final GeneralLedgerLayoutProfileRegistry layouts;
    private final AccountingFileImportReceiptRepository receipts;
    private final AccountingFileImportIdempotencyGuard idempotency;
    private final AccountingMovementBatchPublisher publisher;
    private final AccountingDatasetCoverageService coverage;
    private final int maxRecords;

    public AccountingFileImportConfirmationService(
            AccountingSourceRepository sources,
            CompanyPermissionAuthorizationService permissions,
            GeneralLedgerLayoutProfileRegistry layouts,
            AccountingFileImportReceiptRepository receipts,
            AccountingFileImportIdempotencyGuard idempotency,
            AccountingMovementBatchPublisher publisher,
            AccountingDatasetCoverageService coverage,
            @Value("${finemp.sync.publication.max-records:25000}") int maxRecords) {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("Invalid file import publication resource limit.");
        }
        this.sources = sources;
        this.permissions = permissions;
        this.layouts = layouts;
        this.receipts = receipts;
        this.idempotency = idempotency;
        this.publisher = publisher;
        this.coverage = coverage;
        this.maxRecords = maxRecords;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountingFileImportReceipt confirm(
            UUID actorId,
            UUID accountingSourceId,
            UUID idempotencyKey,
            String expectedFileSha256,
            String profileId,
            AccountingFileFormat format,
            byte[] content) {
        if (actorId == null || accountingSourceId == null || idempotencyKey == null) {
            throw new OrganizationAccessDeniedException();
        }

        String fileSha256 = AccountingFileHash.sha256(content);
        var source = sources.findLockedForPublicationById(accountingSourceId)
                .orElseThrow(() -> conflict(
                        AccountingFileImportConflictException.Reason.SOURCE_NOT_AVAILABLE));

        if (source.getType() != AccountingSourceType.FILE_IMPORT || !source.isActive()) {
            throw conflict(AccountingFileImportConflictException.Reason.SOURCE_NOT_AVAILABLE);
        }
        if (!permissions.hasFreshPermission(
                actorId,
                source.getCompany().getId(),
                CompanyPermission.ACCOUNTING_IMPORT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }

        if (expectedFileSha256 == null
                || !expectedFileSha256.matches("[0-9a-f]{64}")
                || !expectedFileSha256.equals(fileSha256)) {
            throw conflict(AccountingFileImportConflictException.Reason.PREVIEW_FILE_MISMATCH);
        }

        var profile = layouts.require(profileId, format);
        var parsed = profile.parse(content);
        if (!parsed.errors().isEmpty() || parsed.invalidRowCount() > 0) {
            throw new AccountingFilePreviewException(
                    "O arquivo possui linhas inválidas e não pode ser importado.");
        }

        String fingerprint = AccountingFileImportFingerprint.calculate(
                AccountingFileDataset.GENERAL_LEDGER,
                fileSha256,
                profile.id(),
                parsed.periodStart(),
                parsed.periodEnd());

        var replay = idempotency.findMatching(
                accountingSourceId,
                idempotencyKey,
                fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (receipts.findOverlapping(
                accountingSourceId,
                AccountingFileDataset.GENERAL_LEDGER,
                parsed.periodStart(),
                parsed.periodEnd()).isPresent()) {
            throw conflict(AccountingFileImportConflictException.Reason.PERIOD_OVERLAP);
        }

        AccountingMovementBatchPublisher.Result published;
        try {
            var batch = AccountingFileImportNormalizer.normalize(parsed, maxRecords);
            published = publisher.publish(source, batch);
        } catch (SyncPublicationRejectedException rejected) {
            if (rejected.getCode() == SyncFailureCode.DATA_CONFLICT) {
                throw conflict(AccountingFileImportConflictException.Reason.PUBLISHED_DATA_CONFLICT);
            }
            if (rejected.getCode() == SyncFailureCode.INVALID_PAYLOAD
                    || rejected.getCode() == SyncFailureCode.RESOURCE_LIMIT) {
                throw new AccountingFilePreviewException(
                        "O arquivo possui dados contábeis inválidos para importação.");
            }
            throw rejected;
        }

        coverage.record(
                accountingSourceId,
                AccountingDataset.MOVEMENTS,
                parsed.periodStart(),
                parsed.periodEnd());

        var receipt = new AccountingFileImportReceipt(
                accountingSourceId,
                idempotencyKey,
                AccountingFileDataset.GENERAL_LEDGER,
                fileSha256,
                fingerprint,
                profile.id(),
                parsed.periodStart(),
                parsed.periodEnd(),
                parsed.movements().size(),
                published.insertedCount(),
                published.skippedCount(),
                actorId,
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));

        receipts.insert(receipt);
        return receipt;
    }

    private AccountingFileImportConflictException conflict(
            AccountingFileImportConflictException.Reason reason) {
        return new AccountingFileImportConflictException(reason);
    }
}
