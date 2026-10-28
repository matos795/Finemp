package com.finemp.finance.entry;

import jakarta.persistence.*;

/** Original creation mode and count, independent of any allocation of money. */
@Embeddable
public record EntrySeriesDefinition(
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) SeriesMode mode,
        @Column(nullable=false,updatable=false) int quantity) {
    public EntrySeriesDefinition {
        if(mode==null || mode==SeriesMode.SINGLE || quantity<2 || quantity>120)throw new IllegalArgumentException("Definição de série inválida.");
    }
}
