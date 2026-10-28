package com.finemp.finance.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

/** Immutable command result, not a representation of the entry's current state. */
public record FinancialEntryCommandReceipt(UUID companyId,UUID commandId,UUID entryId,EntryCommandAction action,long version,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DeletedEntry> deletedEntries) {
    public FinancialEntryCommandReceipt(UUID companyId,UUID commandId,UUID entryId,EntryCommandAction action,long version) {
        this(companyId,commandId,entryId,action,version,action==EntryCommandAction.DELETED?List.of(new DeletedEntry(entryId,version)):List.of());
    }
    public FinancialEntryCommandReceipt {
        deletedEntries=List.copyOf(deletedEntries);
        if(action==EntryCommandAction.DELETED) {
            if(deletedEntries.isEmpty() || deletedEntries.size()>120 || !deletedEntries.getFirst().entryId().equals(entryId) || deletedEntries.getFirst().version()!=version)throw invalid();
            var ids=new HashSet<UUID>();for(var item:deletedEntries)if(item.entryId()==null || item.version()<0 || !ids.add(item.entryId()))throw invalid();
        }else if(!deletedEntries.isEmpty())throw invalid();
    }
    public record DeletedEntry(UUID entryId,long version) { }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Recibo de exclusão inválido.");}
}
