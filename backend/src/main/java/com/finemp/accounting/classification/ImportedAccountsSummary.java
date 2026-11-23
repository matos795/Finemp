package com.finemp.accounting.classification;

/** Contagens do universo importado filtrado por fonte/busca. */
public record ImportedAccountsSummary(long total, long classified, long pending) {

    public ImportedAccountsSummary(long total, long classified) {
        this(total, classified, total - classified);
    }
}
