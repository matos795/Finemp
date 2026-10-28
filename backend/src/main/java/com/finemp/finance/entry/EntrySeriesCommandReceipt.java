package com.finemp.finance.entry;

import java.util.*;

/** Immutable ordered result of creation, retained independently of current series/member state. */
public record EntrySeriesCommandReceipt(UUID companyId,UUID commandId,UUID seriesId,List<Item> items) {
    public EntrySeriesCommandReceipt {
        if(companyId==null || commandId==null || seriesId==null || items==null || items.size()<2 || items.size()>120)throw invalid();
        items=List.copyOf(items);var ids=new HashSet<UUID>();
        for(int index=0;index<items.size();index++) {
            var item=items.get(index);if(item.number()!=index+1 || item.entryId()==null || item.version()<0 || !ids.add(item.entryId()))throw invalid();
        }
    }
    public record Item(int number,UUID entryId,long version) { }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Recibo de série inválido.");}
}
