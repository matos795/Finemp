package com.finemp.accounting.fileimport;

import java.time.LocalDate;
import java.util.List;

public record GeneralLedgerParsedFile(
        String layoutProfile,
        AccountingFileFormat format,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalRows,
        int invalidRowCount,
        List<GeneralLedgerObservedAccount> accounts,
        List<GeneralLedgerPreviewIssue> warnings,
        List<GeneralLedgerPreviewIssue> errors,
        List<GeneralLedgerParsedMovement> movements) {

    private static final int PREVIEW_SAMPLE_LIMIT = 20;

    public GeneralLedgerParsedFile {
        accounts = List.copyOf(accounts);
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
        movements = List.copyOf(movements);
    }

    public GeneralLedgerPreview toPreview() {
        return new GeneralLedgerPreview(
                layoutProfile,
                format,
                periodStart,
                periodEnd,
                totalRows,
                movements.size(),
                invalidRowCount,
                accounts.size(),
                warnings,
                errors,
                movements.stream()
                        .limit(PREVIEW_SAMPLE_LIMIT)
                        .map(GeneralLedgerParsedMovement::toPreviewRow)
                        .toList());
    }
}
