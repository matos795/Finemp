package com.finemp.accounting.fileimport;

import com.finemp.security.UserAccountPrincipal;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public final class AccountingFileImportController {
    private final AccountingFileImportPreviewService previews;
    private final AccountingFileImportConfirmationService confirmations;

    public AccountingFileImportController(
            AccountingFileImportPreviewService previews,
            AccountingFileImportConfirmationService confirmations) {
        this.previews = previews;
        this.confirmations = confirmations;
    }

    @PostMapping(
            path = "/api/accounting-sources/{accountingSourceId}/file-imports/general-ledger/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AccountingFilePreviewResponse preview(
            @PathVariable UUID accountingSourceId,
            @RequestParam String layoutProfile,
            @RequestParam AccountingFileFormat format,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserAccountPrincipal principal) {
        if (principal == null) {
            throw new AccountingFilePreviewException("Usuário não autenticado.");
        }
        try {
            return previews.preview(
                    principal.getUserAccountId(),
                    accountingSourceId,
                    layoutProfile,
                    format,
                    file.getBytes());
        } catch (IOException unreadable) {
            throw new AccountingFilePreviewException("Não foi possível ler o arquivo enviado.");
        }
    }
    @PostMapping(
            path = "/api/accounting-sources/{accountingSourceId}/file-imports/general-ledger/confirm",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AccountingFileImportReceiptResponse confirm(
            @PathVariable UUID accountingSourceId,
            @RequestParam UUID idempotencyKey,
            @RequestParam String layoutProfile,
            @RequestParam AccountingFileFormat format,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserAccountPrincipal principal) {
        if (principal == null) {
            throw new AccountingFilePreviewException("Usuário não autenticado.");
        }
        try {
            return AccountingFileImportReceiptResponse.from(confirmations.confirm(
                    principal.getUserAccountId(),
                    accountingSourceId,
                    idempotencyKey,
                    layoutProfile,
                    format,
                    file.getBytes()));
        } catch (IOException unreadable) {
            throw new AccountingFilePreviewException("Não foi possível ler o arquivo enviado.");
        }
    }

}
