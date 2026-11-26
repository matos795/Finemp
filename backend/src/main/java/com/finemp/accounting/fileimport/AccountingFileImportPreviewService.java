package com.finemp.accounting.fileimport;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class AccountingFileImportPreviewService {
    private final AccountingFileImportFoundationService foundation;

    public AccountingFileImportPreviewService(AccountingFileImportFoundationService foundation) {
        this.foundation = foundation;
    }

    public AccountingFilePreviewResponse preview(
            UUID actorId,
            UUID accountingSourceId,
            String profileId,
            AccountingFileFormat format,
            byte[] content) {
        String fileSha256 = AccountingFileHash.sha256(content);
        var profile = foundation.requireLayout(actorId, accountingSourceId, profileId, format);
        var preview = profile.preview(content);
        return AccountingFilePreviewResponse.from(fileSha256, preview);
    }
}
