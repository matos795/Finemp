package com.finemp.finance.entry;

import java.util.UUID;

/** Immutable command result, not a representation of the entry's current state. */
public record FinancialEntryCommandReceipt(UUID companyId,UUID commandId,UUID entryId,EntryCommandAction action,long version) { }
