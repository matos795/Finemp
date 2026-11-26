package com.finemp.accounting.fileimport;

import java.time.LocalDate;
import java.util.List;

public record GeneralLedgerPreview(
        String layoutProfile,
        AccountingFileFormat format,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalRows,
        int movementCount,
        int invalidRowCount,
        int observedAccountCount,
        List<GeneralLedgerPreviewIssue> warnings,
        List<GeneralLedgerPreviewIssue> errors,
        List<GeneralLedgerPreviewRow> sampleRows) {

    public GeneralLedgerPreview {
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
        sampleRows = List.copyOf(sampleRows);
    }
}
