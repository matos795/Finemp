package com.finemp.accounting.fileimport;

import com.finemp.accounting.source.AccountingConnectionMode;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceType;
import java.util.UUID;

public record AccountingFileImportSourceResponse(
        UUID id,
        AccountingSourceType type,
        AccountingConnectionMode connectionMode,
        boolean active) {

    static AccountingFileImportSourceResponse from(AccountingSource source) {
        return new AccountingFileImportSourceResponse(
                source.getId(),
                source.getType(),
                source.getConnectionMode(),
                source.isActive());
    }
}
