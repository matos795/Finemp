package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.company.Company;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountingFileImportGuardsTest {
    private final AccountingFileImportReceiptRepository receipts =
            mock(AccountingFileImportReceiptRepository.class);

    @Test
    void shouldReturnAnIdenticalReceiptAndRejectTheSameKeyWithAnotherFingerprint() {
        UUID sourceId = UUID.randomUUID();
        UUID command = UUID.randomUUID();
        String fingerprint = "a".repeat(64);
        var receipt = receipt(sourceId, command, fingerprint);
        var guard = new AccountingFileImportIdempotencyGuard(receipts);
        when(receipts.find(sourceId, command)).thenReturn(Optional.of(receipt));

        assertThat(guard.findMatching(sourceId, command, fingerprint))
                .contains(receipt);

        assertThatThrownBy(() ->
                guard.findMatching(sourceId, command, "b".repeat(64)))
                .isInstanceOfSatisfying(
                        AccountingFileImportConflictException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(AccountingFileImportConflictException.Reason.IDEMPOTENCY_MISMATCH));
    }

    @Test
    void shouldRevalidateSourceTypeActivityPermissionAndExplicitLayout() {
        var sources = mock(AccountingSourceRepository.class);
        var permissions = mock(CompanyPermissionAuthorizationService.class);
        GeneralLedgerLayoutProfile profile = new GeneralLedgerLayoutProfile() {
            @Override public String id() { return "synthetic-v1"; }
            @Override public Set<AccountingFileFormat> formats() {
                return Set.of(AccountingFileFormat.CSV);
            }
        };
        var service = new AccountingFileImportFoundationService(
                sources,
                permissions,
                new GeneralLedgerLayoutProfileRegistry(List.of(profile)));

        UUID actor = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        var company = mock(Company.class);
        var source = mock(AccountingSource.class);
        when(company.getId()).thenReturn(companyId);
        when(source.getType()).thenReturn(AccountingSourceType.FILE_IMPORT);
        when(source.isActive()).thenReturn(true);
        when(source.getCompany()).thenReturn(company);
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.of(source));
        when(permissions.hasFreshPermission(
                actor,
                companyId,
                CompanyPermission.ACCOUNTING_IMPORT_MANAGE)).thenReturn(true);

        assertThat(service.requireLayout(
                actor,
                sourceId,
                "synthetic-v1",
                AccountingFileFormat.CSV))
                .isSameAs(profile);

        assertThatThrownBy(() ->
                service.requireLayout(actor, sourceId, "unknown", AccountingFileFormat.CSV))
                .isInstanceOf(AccountingFileLayoutUnavailableException.class);
    }

    @Test
    void shouldFailBeforeLayoutResolutionForWrongSourceOrRevokedPermission() {
        var sources = mock(AccountingSourceRepository.class);
        var permissions = mock(CompanyPermissionAuthorizationService.class);
        var layouts = mock(GeneralLedgerLayoutProfileRegistry.class);
        var service = new AccountingFileImportFoundationService(sources, permissions, layouts);
        UUID actor = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        var questor = mock(AccountingSource.class);
        when(questor.getType()).thenReturn(AccountingSourceType.QUESTOR);
        when(questor.isActive()).thenReturn(true);
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.of(questor));

        assertThatThrownBy(() ->
                service.requireLayout(actor, sourceId, "synthetic", AccountingFileFormat.CSV))
                .isInstanceOfSatisfying(
                        AccountingFileImportConflictException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(AccountingFileImportConflictException.Reason.SOURCE_NOT_AVAILABLE));
        verifyNoInteractions(permissions, layouts);

        var company = mock(Company.class);
        when(company.getId()).thenReturn(UUID.randomUUID());
        var fileSource = mock(AccountingSource.class);
        when(fileSource.getType()).thenReturn(AccountingSourceType.FILE_IMPORT);
        when(fileSource.isActive()).thenReturn(true);
        when(fileSource.getCompany()).thenReturn(company);
        when(sources.findForReadingById(sourceId)).thenReturn(Optional.of(fileSource));

        assertThatThrownBy(() ->
                service.requireLayout(actor, sourceId, "synthetic", AccountingFileFormat.CSV))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        verify(permissions).hasFreshPermission(
                actor,
                company.getId(),
                CompanyPermission.ACCOUNTING_IMPORT_MANAGE);
        verifyNoInteractions(layouts);
    }

    private AccountingFileImportReceipt receipt(
            UUID sourceId,
            UUID command,
            String fingerprint) {
        return new AccountingFileImportReceipt(
                sourceId,
                command,
                AccountingFileDataset.GENERAL_LEDGER,
                "a".repeat(64),
                fingerprint,
                "synthetic-v1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                10,
                10,
                0,
                UUID.randomUUID(),
                Instant.parse("2026-02-01T12:00:00Z"));
    }
}
